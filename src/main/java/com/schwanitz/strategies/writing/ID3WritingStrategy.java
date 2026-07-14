package com.schwanitz.strategies.writing;

import com.schwanitz.io.*;
import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.strategies.parsing.factory.TagParsingStrategyFactory;
import com.schwanitz.strategies.writing.context.TagWritingStrategy;
import com.schwanitz.tagging.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.Objects;

/**
 * Schreib-Strategie für ID3-Tags (ID3v1, ID3v1.1, ID3v2.2, ID3v2.3 und ID3v2.4).
 * <p>
 * <b>ID3v1/v1.1:</b> In-Place-Schreiben am Dateiende (feste 128 Bytes).<br>
 * <b>ID3v2.x:</b> Neuschreiben am Dateianfang. Bei Größenänderung muss die gesamte
 * Datei (Tag + Audiodaten) in eine temporäre Datei geschrieben werden.
 * </p>
 */
public class ID3WritingStrategy extends AbstractTagWritingStrategy implements TagWritingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(ID3WritingStrategy.class);

    private static final int ID3V1_TAG_SIZE = 128;
    private static final int ID3V1_HEADER_SIZE = 3;
    private static final int ID3V2_HEADER_SIZE = 10;
    private static final String ID3V1_SIGNATURE = "TAG";
    private static final String ID3V2_SIGNATURE = "ID3";

    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    private static final String[] ID3V1_GENRES = {
            "Blues", "Classic Rock", "Country", "Dance", "Disco",
            "Funk", "Grunge", "Hip-Hop", "Jazz", "Metal",
            "New Age", "Oldies", "Other", "Pop", "R&B",
            "Rap", "Reggae", "Rock", "Techno", "Industrial",
            "Alternative", "Ska", "Death Metal", "Pranks", "Soundtrack",
            "Euro-Techno", "Ambient", "Trip-Hop", "Vocal", "Jazz+Funk",
            "Fusion", "Trance", "Classical", "Instrumental", "Acid",
            "House", "Game", "Sound Clip", "Gospel", "Noise",
            "AlternRock", "Bass", "Soul", "Punk", "Space",
            "Meditative", "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic",
            "Darkwave", "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance",
            "Dream", "Southern Rock", "Comedy", "Cult", "Gangsta",
            "Top 40", "Christian Rap", "Pop/Funk", "Jungle", "Native American",
            "Cabaret", "New Wave", "Psychadelic", "Rave", "Showtunes",
            "Trailer", "Lo-Fi", "Tribal", "Acid Punk", "Acid Jazz",
            "Polka", "Retro", "Musical", "Rock & Roll", "Hard Rock"
    };

    /**
     * Erzeugt eine neue ID3-Schreib-Strategie.
     *
     * @param parsingFactory die Factory zum Lesen bestehender Tags
     */
    public ID3WritingStrategy(TagParsingStrategyFactory parsingFactory) {
        super("ID3", parsingFactory);
    }

    @Override
    public List<TagFormat> getSupportedWriteFormats() {
        return List.of(TagFormat.ID3V1, TagFormat.ID3V1_1,
                TagFormat.ID3V2_2, TagFormat.ID3V2_3, TagFormat.ID3V2_4);
    }

    @Override
    public boolean supportsInPlaceWrite(TagFormat format) {
        return switch (format) {
            case ID3V1, ID3V1_1 -> true;
            default -> false;
        };
    }

    @Override
    public WriteResult writeTag(TagFormat format, Metadata metadata,
                                 SeekableDataSource source, TagInfo existingTag,
                                 WriteConfiguration config) throws IOException {
        validateInput(metadata, source, format);
        Objects.requireNonNull(config, "config must not be null");

        return switch (format) {
            case ID3V1, ID3V1_1 -> writeID3v1(metadata, source, existingTag, format, config);
            case ID3V2_2, ID3V2_3, ID3V2_4 -> writeID3v2(metadata, source, existingTag, format, config);
            default -> WriteResult.failure(format, "Nicht unterstütztes ID3-Format: " + format);
        };
    }

    // ================================
    // ID3v1 Schreiblogik
    // ================================

    private WriteResult writeID3v1(Metadata metadata, SeekableDataSource source,
                                    TagInfo existingTag, TagFormat format,
                                    WriteConfiguration config) throws IOException {
        long existingTagOffset = existingTag != null ? existingTag.getOffset() : source.length();
        long existingTagSize = existingTag != null ? existingTag.getSize() : 0;

        byte[] tagData = new byte[ID3V1_TAG_SIZE];
        writeID3v1Fields(tagData, metadata, format);

        if (config.inPlace() && existingTag != null && existingTag.getSize() == ID3V1_TAG_SIZE) {
            // In-Place-Schreiben
            try (SeekableDataSink sink = SeekableDataSinks.forBytes()) {
                sink.write(existingTagOffset, tagData);
                LOG.debug("ID3v1 In-Place geschrieben an Offset {}", existingTagOffset);
            }
            // Für In-Place bei SeekableDataSource: Wir müssen die Datei direkt schreiben
            writeInPlaceToFile(source, existingTagOffset, tagData);
            return WriteResult.success(format, existingTagSize, ID3V1_TAG_SIZE,
                    "ID3v1 In-Place geschrieben");
        } else {
            // Temp-Datei Ansatz
            return writeID3v1ViaTempFile(metadata, source, existingTag, format, config, tagData);
        }
    }

    private void writeID3v1Fields(byte[] data, Metadata metadata, TagFormat format) {
        // "TAG" Signatur
        System.arraycopy(ID3V1_SIGNATURE.getBytes(StandardCharsets.ISO_8859_1), 0, data, 0, 3);

        // Fixed-length Felder mit Nullen initialisiert
        writeFixedField(data, 3, getFieldString(metadata, "TIT2"), 30);
        writeFixedField(data, 33, getFieldString(metadata, "TPE1"), 30);
        writeFixedField(data, 63, getFieldString(metadata, "TALB"), 30);
        writeFixedField(data, 93, getFieldString(metadata, "TYER"), 4);

        if (format == TagFormat.ID3V1_1) {
            writeFixedField(data, 97, getFieldString(metadata, "COMM"), 28);
            String track = getFieldString(metadata, "TRCK");
            if (!track.isEmpty()) {
                try {
                    data[126] = (byte) Integer.parseInt(track);
                } catch (NumberFormatException e) {
                    // Ignorieren
                }
            }
        } else {
            writeFixedField(data, 97, getFieldString(metadata, "COMM"), 30);
        }

        // Genre
        String genre = getFieldString(metadata, "TCON");
        if (!genre.isEmpty()) {
            int genreIndex = findGenreIndex(genre);
            data[127] = (byte) genreIndex;
        }
    }

    private void writeFixedField(byte[] data, int offset, String value, int maxLength) {
        byte[] bytes = value.getBytes(StandardCharsets.ISO_8859_1);
        int length = Math.min(bytes.length, maxLength);
        System.arraycopy(bytes, 0, data, offset, length);
        // Restliche Bytes bleiben null (bereits initialisiert)
    }

    private int findGenreIndex(String genre) {
        for (int i = 0; i < ID3V1_GENRES.length; i++) {
            if (ID3V1_GENRES[i].equalsIgnoreCase(genre)) {
                return i;
            }
        }
        return 0; // "Other"
    }

    private WriteResult writeID3v1ViaTempFile(Metadata metadata, SeekableDataSource source,
                                               TagInfo existingTag, TagFormat format,
                                               WriteConfiguration config, byte[] tagData) throws IOException {
        try (ByteArraySink output = new ByteArraySink(4096)) {
            long audioLength;
            if (existingTag != null) {
                audioLength = existingTag.getOffset();
            } else {
                audioLength = source.length();
            }

            // Audiodaten kopieren
            if (audioLength > 0) {
                copyAudioData(source, output, 0, 0, COPY_BUFFER_SIZE);
            }

            // ID3v1 Tag anhängen
            output.write(audioLength, tagData);

            Files.write(Path.of(source.name()), output.toByteArray());

            return WriteResult.success(format, existingTag != null ? existingTag.getSize() : 0,
                    ID3V1_TAG_SIZE, "ID3v1 geschrieben via Temp-Datei");
        }
    }

    // ================================
    // ID3v2 Schreiblogik
    // ================================

    private WriteResult writeID3v2(Metadata metadata, SeekableDataSource source,
                                    TagInfo existingTag, TagFormat format,
                                    WriteConfiguration config) throws IOException {
        Charset charset = "ISO-8859-1".equals(config.encoding()) ?
                StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_8;
        int majorVersion = format == TagFormat.ID3V2_2 ? 2 :
                config.id3Version() == 3 ? 3 : 4;

        // Neues Tag als Byte-Array serialisieren
        byte[] tagBytes = serializeID3v2(metadata, format, majorVersion, charset);

        long oldTagSize = existingTag != null ? existingTag.getSize() : 0;
        long audioStart = existingTag != null ? existingTag.getOffset() + existingTag.getSize() : 0;

        try (ByteArraySink output = new ByteArraySink(4096)) {
            // Neues ID3v2 Tag schreiben
            output.write(0, tagBytes);

            // Audiodaten kopieren
            long sinkOffset = tagBytes.length;
            long audioBytes = copyAudioData(source, output, audioStart, sinkOffset, COPY_BUFFER_SIZE);

            // ID3v1 Tag kopieren (falls vorhanden, am Dateiende)
            if (existingTag != null) {
                // Prüfe ob am Dateiende ein ID3v1 Tag ist
                long fileEnd = source.length();
                if (fileEnd - (existingTag.getOffset() + existingTag.getSize()) >= ID3V1_TAG_SIZE) {
                    long id3v1Offset = fileEnd - ID3V1_TAG_SIZE;
                    byte[] id3v1 = new byte[ID3V1_TAG_SIZE];
                    source.readFully(id3v1Offset, id3v1);
                    if (new String(id3v1, 0, 3, StandardCharsets.ISO_8859_1).equals("TAG")) {
                        output.write(sinkOffset + audioBytes, id3v1);
                    }
                }
            }

            Files.write(Path.of(source.name()), output.toByteArray());
        }

        LOG.debug("ID3v2.{} geschrieben: {} Bytes Tag + Audiodaten",
                majorVersion, tagBytes.length);

        return WriteResult.success(format, oldTagSize, tagBytes.length,
                "ID3v2." + majorVersion + " geschrieben (" + tagBytes.length + " Bytes)");
    }

    /**
     * Serialisiert Metadaten als ID3v2 Tag (Header + Frames) in ein Byte-Array.
     */
    private byte[] serializeID3v2(Metadata metadata, TagFormat format, int majorVersion, Charset charset) {
        // Zuerst alle Frames serialisieren
        byte[] frameData = serializeAllFrames(metadata, majorVersion, charset);

        // Header-Größe = Synchsafe(frameData.length)
        byte[] header = new byte[ID3V2_HEADER_SIZE];
        header[0] = 'I';
        header[1] = 'D';
        header[2] = '3';
        header[3] = (byte) majorVersion;
        header[4] = 0; // Revision

        // Flags: 0 für ID3v2.2/2.3, optional flags für 2.4
        header[5] = 0;

        // Synchsafe Integer für Tag-Größe
        int tagSize = frameData.length;
        header[6] = (byte) ((tagSize >> 21) & 0x7F);
        header[7] = (byte) ((tagSize >> 14) & 0x7F);
        header[8] = (byte) ((tagSize >> 7) & 0x7F);
        header[9] = (byte) (tagSize & 0x7F);

        // Header + Frame-Daten zusammenfügen
        byte[] result = new byte[ID3V2_HEADER_SIZE + frameData.length];
        System.arraycopy(header, 0, result, 0, ID3V2_HEADER_SIZE);
        System.arraycopy(frameData, 0, result, ID3V2_HEADER_SIZE, frameData.length);

        return result;
    }

    /**
     * Serialisiert alle Metadaten-Felder als ID3v2 Frames.
     */
    private byte[] serializeAllFrames(Metadata metadata, int majorVersion, Charset charset) {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();

        for (MetadataField<?> field : metadata.getFields()) {
            byte[] frame = serializeFrame(field, majorVersion, charset);
            if (frame != null) {
                buffer.write(frame, 0, frame.length);
            }
        }

        return buffer.toByteArray();
    }

    /**
     * Serialisiert ein einzelnes Metadatenfeld als ID3v2 Frame.
     */
    private byte[] serializeFrame(MetadataField<?> field, int majorVersion, Charset charset) {
        String frameId = field.getKey();
        Object valueObj = field.getValue();
        if (valueObj == null) return null;
        String value = valueObj.toString();
        if (value.isEmpty()) return null;

        byte[] frameBody;

        // Spezialbehandlung für bestimmte Frame-Typen
        if (frameId.equals("APIC") || frameId.equals("PIC")) {
            // Picture-Frame: Überspringen (erfordert spezielle Behandlung)
            LOG.debug("Picture-Frame {} wird übersprungen (noch nicht implementiert)", frameId);
            return null;
        } else if (frameId.equals("COMM") || frameId.equals("COM")) {
            frameBody = serializeCommentFrame(value, charset);
        } else if (frameId.equals("USLT") || frameId.equals("ULT")) {
            frameBody = serializeLyricsFrame(value, charset);
        } else if (frameId.equals("TXXX") || frameId.equals("TXX")) {
            frameBody = serializeUserDefinedTextFrame(value, charset);
        } else {
            // Standard Text-Frame
            frameBody = serializeTextFrame(value, charset);
        }

        // Frame-Header
        return buildFrameHeader(frameId, frameBody, majorVersion);
    }

    /**
     * Baut einen ID3v2 Frame-Header mit Frame-ID, Größe und Flags.
     */
    private byte[] buildFrameHeader(String frameId, byte[] body, int majorVersion) {
        byte[] header;

        if (majorVersion == 2) {
            // ID3v2.2: 3-Zeichen Frame-ID, 3-Byte Größe (kein Synchsafe)
            header = new byte[6];
            byte[] idBytes = frameId.getBytes(StandardCharsets.US_ASCII);
            int idLen = Math.min(idBytes.length, 3);
            System.arraycopy(idBytes, 0, header, 0, idLen);

            header[3] = (byte) ((body.length >> 16) & 0xFF);
            header[4] = (byte) ((body.length >> 8) & 0xFF);
            header[5] = (byte) (body.length & 0xFF);
        } else {
            // ID3v2.3/2.4: 4-Zeichen Frame-ID, 4-Byte Größe, 2-Byte Flags
            header = new byte[10];
            byte[] idBytes = frameId.getBytes(StandardCharsets.US_ASCII);
            int idLen = Math.min(idBytes.length, 4);
            System.arraycopy(idBytes, 0, header, 0, idLen);

            if (majorVersion == 4) {
                // Synchsafe Frame-Größe
                header[4] = (byte) ((body.length >> 21) & 0x7F);
                header[5] = (byte) ((body.length >> 14) & 0x7F);
                header[6] = (byte) ((body.length >> 7) & 0x7F);
                header[7] = (byte) (body.length & 0x7F);
            } else {
                // Normale 32-Bit Größe
                header[4] = (byte) ((body.length >> 24) & 0xFF);
                header[5] = (byte) ((body.length >> 16) & 0xFF);
                header[6] = (byte) ((body.length >> 8) & 0xFF);
                header[7] = (byte) (body.length & 0xFF);
            }

            // Flags = 0
            header[8] = 0;
            header[9] = 0;
        }

        byte[] result = new byte[header.length + body.length];
        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(body, 0, result, header.length, body.length);
        return result;
    }

    /**
     * Serialisiert einen Standard-Text-Frame (T*).
     */
    private byte[] serializeTextFrame(String value, Charset charset) {
        byte[] textBytes = value.getBytes(charset);
        byte[] body = new byte[1 + textBytes.length];
        // Encoding-Byte: 0 = ISO-8859-1, 1 = UTF-16, 2 = UTF-16BE, 3 = UTF-8
        body[0] = (byte) (charset.equals(StandardCharsets.UTF_8) ? 3 :
                charset.equals(StandardCharsets.US_ASCII) ? 0 : 0);
        System.arraycopy(textBytes, 0, body, 1, textBytes.length);
        return body;
    }

    /**
     * Serialisiert einen COMM-Frame (Kommentar).
     */
    private byte[] serializeCommentFrame(String value, Charset charset) {
        byte[] textBytes = value.getBytes(charset);
        byte[] body = new byte[4 + textBytes.length];
        body[0] = (byte) (charset.equals(StandardCharsets.UTF_8) ? 3 : 0); // Encoding
        body[1] = 'e'; // Language: English
        body[2] = 'n';
        body[3] = 'g';
        // Short description (empty) + null terminator
        System.arraycopy(textBytes, 0, body, 4, textBytes.length);
        return body;
    }

    /**
     * Serialisiert einen USLT-Frame (Lyrics).
     */
    private byte[] serializeLyricsFrame(String value, Charset charset) {
        byte[] textBytes = value.getBytes(charset);
        byte[] body = new byte[4 + textBytes.length];
        body[0] = (byte) (charset.equals(StandardCharsets.UTF_8) ? 3 : 0);
        body[1] = 'e';
        body[2] = 'n';
        body[3] = 'g';
        System.arraycopy(textBytes, 0, body, 4, textBytes.length);
        return body;
    }

    /**
     * Serialisiert einen TXXX-Frame (User Defined Text).
     */
    private byte[] serializeUserDefinedTextFrame(String value, Charset charset) {
        byte[] textBytes = value.getBytes(charset);
        byte[] body = new byte[2 + textBytes.length];
        body[0] = (byte) (charset.equals(StandardCharsets.UTF_8) ? 3 : 0);
        // Description leer + null terminator
        System.arraycopy(textBytes, 0, body, 2, textBytes.length);
        return body;
    }

    // ================================
    // Hilfsmethoden
    // ================================

    private String getFieldString(Metadata metadata, String key) {
        for (MetadataField<?> field : metadata.getFields()) {
            if (field.getKey().equals(key)) {
                Object value = field.getValue();
                return value != null ? value.toString() : "";
            }
        }
        return "";
    }

    private void writeInPlaceToFile(SeekableDataSource source, long offset, byte[] data) throws IOException {
        try (ByteArraySink output = new ByteArraySink(4096)) {
            copyAudioData(source, output, 0, 0, COPY_BUFFER_SIZE);
            output.write(offset, data);
            Files.write(Path.of(source.name()), output.toByteArray());
        }
    }
}
