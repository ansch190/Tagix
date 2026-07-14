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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.Objects;

/**
 * Schreib-Strategie für Vorbis Comment Tags (OGG Vorbis und FLAC).
 * <p>
 * <b>OGG Vorbis:</b> Das Comment-Paket wird neu serialisiert und in neue OGG-Seiten
 * eingebettet. Der gesamte OGG-Stream wird in eine Temp-Datei geschrieben.<br>
 * <b>FLAC:</b> Der Vorbis Comment Metadata-Block (Typ 4) wird neu geschrieben.
 * </p>
 */
public class VorbisWritingStrategy extends AbstractTagWritingStrategy implements TagWritingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(VorbisWritingStrategy.class);

    private static final int VORBIS_COMMENT_PACKET_TYPE = 0x03;
    private static final String VORBIS_MAGIC = "vorbis";
    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    private static final int FLAC_SIGNATURE_LENGTH = 4;
    private static final int FLAC_BLOCK_HEADER_SIZE = 4;
    private static final int FLAC_LAST_BLOCK_FLAG = 0x80;
    private static final int FLAC_BLOCK_TYPE_MASK = 0x7F;
    private static final int FLAC_VORBIS_COMMENT_BLOCK_TYPE = 4;

    /**
     * Erzeugt eine neue Vorbis-Schreib-Strategie.
     *
     * @param parsingFactory die Factory zum Lesen bestehender Tags
     */
    public VorbisWritingStrategy(TagParsingStrategyFactory parsingFactory) {
        super("Vorbis", parsingFactory);
    }

    @Override
    public List<TagFormat> getSupportedWriteFormats() {
        return List.of(TagFormat.VORBIS_COMMENT);
    }

    @Override
    public boolean supportsInPlaceWrite(TagFormat format) {
        return false; // Vorbis Comment erfordert immer Neuschreiben
    }

    @Override
    public WriteResult writeTag(TagFormat format, Metadata metadata,
                                 SeekableDataSource source, TagInfo existingTag,
                                 WriteConfiguration config) throws IOException {
        validateInput(metadata, source, format);
        Objects.requireNonNull(config, "config must not be null");

        long sourceLength = source.length();
        if (sourceLength < FLAC_SIGNATURE_LENGTH) {
            return WriteResult.failure(format, "Datei zu klein");
        }

        // Prüfe ob OGG oder FLAC
        byte[] sig = new byte[FLAC_SIGNATURE_LENGTH];
        source.readFully(0, sig);
        String signature = new String(sig, StandardCharsets.US_ASCII);

        if ("OggS".equals(signature)) {
            return writeVorbisCommentOGG(metadata, source, existingTag, config);
        } else if ("fLaC".equals(signature)) {
            return writeVorbisCommentFLAC(metadata, source, existingTag, config);
        } else {
            return WriteResult.failure(format, "Kein OGG oder FLAC erkannt");
        }
    }

    // ================================
    // OGG Vorbis Schreiblogik
    // ================================

    private WriteResult writeVorbisCommentOGG(Metadata metadata, SeekableDataSource source,
                                               TagInfo existingTag, WriteConfiguration config) throws IOException {
        // Neuen Comment-Block serialisieren
        byte[] commentPacket = serializeVorbisComment(metadata);

        long oldSize = existingTag != null ? existingTag.getSize() : 0;

        try (ByteArraySink output = new ByteArraySink(4096)) {
            long sourceLength = source.length();
            long sinkPos = 0;

            if (existingTag != null) {
                // Kopiere alles VOR dem Comment-Paket
                if (existingTag.getOffset() > 0) {
                    sinkPos = copyAudioData(source, output, 0, 0, COPY_BUFFER_SIZE);
                }

                // Neuen Comment-Paket in OGG-Seite(n) schreiben
                sinkPos = writeOGGPage(output, sinkPos, commentPacket);

                // Kopiere alles NACH dem Comment-Paket
                long afterTagOffset = existingTag.getOffset() + existingTag.getSize();
                if (afterTagOffset < sourceLength) {
                    copyAudioData(source, output, afterTagOffset, sinkPos, COPY_BUFFER_SIZE);
                }
            } else {
                // Kein bestehendes Tag: Kopiere alles und füge Comment hinzu
                sinkPos = copyAudioData(source, output, 0, 0, COPY_BUFFER_SIZE);
                writeOGGPage(output, sinkPos, commentPacket);
            }

            Files.write(Path.of(source.name()), output.toByteArray());

            return WriteResult.success(TagFormat.VORBIS_COMMENT, oldSize, commentPacket.length,
                    "Vorbis Comment (OGG) geschrieben");
        }
    }

    /**
     * Schreibt einen Vorbis Comment als OGG-Seite.
     */
    private long writeOGGPage(SeekableDataSink sink, long position, byte[] packet) throws IOException {
        // Vereinfachte OGG-Seite: Header(27B) + Segment Table + Packet Data
        int headerSize = 27;
        int segmentCount = (packet.length / 255) + 1;
        byte[] header = new byte[headerSize + segmentCount];

        // OGG Capture Pattern
        header[0] = 'O';
        header[1] = 'g';
        header[2] = 'g';
        header[3] = 'S';

        // Header Version
        header[4] = 0;

        // Header Type: Beginne eines Streams
        header[5] = 0x02;

        // Granule Position (8 Bytes, LE)
        // Timestamp bleibt 0

        // Serial Number (4 Bytes, LE) - zufällige Nummer
        int serial = (int) (System.nanoTime() & 0xFFFFFFFFL);
        header[10] = (byte) (serial & 0xFF);
        header[11] = (byte) ((serial >> 8) & 0xFF);
        header[12] = (byte) ((serial >> 16) & 0xFF);
        header[13] = (byte) ((serial >> 24) & 0xFF);

        // Page Sequence Number (4 Bytes, LE)
        header[14] = 0;
        header[15] = 0;
        header[16] = 0;
        header[17] = 0;

        // Checksum (4 Bytes, LE) - wird 0 gelassen (vereinfacht)
        // header[18-21] = 0

        // Page Segments
        header[22] = (byte) segmentCount;

        // Segment Table
        int remaining = packet.length;
        for (int i = 0; i < segmentCount; i++) {
            int segLen = Math.min(remaining, 255);
            header[23 + i] = (byte) segLen;
            remaining -= segLen;
        }

        // Header + Packet schreiben
        sink.write(position, header);
        sink.write(position + header.length, packet);

        return position + header.length + packet.length;
    }

    // ================================
    // FLAC Schreiblogik
    // ================================

    private WriteResult writeVorbisCommentFLAC(Metadata metadata, SeekableDataSource source,
                                                TagInfo existingTag, WriteConfiguration config) throws IOException {
        byte[] commentBlock = serializeVorbisCommentBlock(metadata);
        long oldSize = existingTag != null ? existingTag.getSize() : 0;

        try (ByteArraySink output = new ByteArraySink(4096)) {
            long sourceLength = source.length();

            if (existingTag != null) {
                // Kopiere alles VOR dem Vorbis Comment Block
                if (existingTag.getOffset() > 0) {
                    copyAudioData(source, output, 0, 0, COPY_BUFFER_SIZE);
                }

                // Neuen Block schreiben
                output.write(existingTag.getOffset(), commentBlock);

                // Kopiere alles NACH dem Block
                long afterBlockOffset = existingTag.getOffset() + existingTag.getSize();
                long sinkOffset = existingTag.getOffset() + commentBlock.length;
                if (afterBlockOffset < sourceLength) {
                    copyAudioData(source, output, afterBlockOffset, sinkOffset, COPY_BUFFER_SIZE);
                }
            } else {
                // Kein bestehendes Tag: "fLaC" + neuer Block + Rest
                byte[] fLaC = new byte[FLAC_SIGNATURE_LENGTH];
                source.readFully(0, fLaC);
                output.write(0, fLaC);

                // Neuen Block nach fLaC schreiben
                output.write(FLAC_SIGNATURE_LENGTH, commentBlock);

                // Rest kopieren
                copyAudioData(source, output, FLAC_SIGNATURE_LENGTH,
                        FLAC_SIGNATURE_LENGTH + commentBlock.length, COPY_BUFFER_SIZE);
            }

            Files.write(Path.of(source.name()), output.toByteArray());

            return WriteResult.success(TagFormat.VORBIS_COMMENT, oldSize, commentBlock.length,
                    "Vorbis Comment (FLAC) geschrieben");
        }
    }

    // ================================
    // Serialisierung
    // ================================

    /**
     * Serialisiert Metadaten als Vorbis Comment Block (mit Block-Header für FLAC).
     */
    private byte[] serializeVorbisCommentBlock(Metadata metadata) {
        byte[] commentData = serializeVorbisComment(metadata);

        // FLAC Block-Header: isLast(1bit) | type(7bit) + length(24bit)
        byte[] block = new byte[FLAC_BLOCK_HEADER_SIZE + commentData.length];
        // Block-Typ 4 (Vorbis Comment), nicht letzter Block
        block[0] = (byte) (FLAC_VORBIS_COMMENT_BLOCK_TYPE & 0x7F);
        int dataLen = commentData.length;
        block[1] = (byte) ((dataLen >> 16) & 0xFF);
        block[2] = (byte) ((dataLen >> 8) & 0xFF);
        block[3] = (byte) (dataLen & 0xFF);

        System.arraycopy(commentData, 0, block, FLAC_BLOCK_HEADER_SIZE, commentData.length);
        return block;
    }

    /**
     * Serialisiert Metadaten als Vorbis Comment Packet.
     */
    private byte[] serializeVorbisComment(Metadata metadata) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        // Packet Type (0x03) + "vorbis" (für OGG)
        buffer.write(VORBIS_COMMENT_PACKET_TYPE);
        buffer.write(VORBIS_MAGIC.getBytes(StandardCharsets.US_ASCII), 0, VORBIS_MAGIC.length());

        // Vendor String
        String vendor = "Tagix";
        byte[] vendorBytes = vendor.getBytes(StandardCharsets.UTF_8);
        writeLittleEndianInt32(buffer, vendorBytes.length);
        buffer.write(vendorBytes, 0, vendorBytes.length);

        // Comment Count
        int commentCount = metadata.getFields().size();
        writeLittleEndianInt32(buffer, commentCount);

        // Comments
        for (MetadataField<?> field : metadata.getFields()) {
            String key = field.getKey();
            Object valueObj = field.getValue();
            String value = valueObj != null ? valueObj.toString() : "";

            String comment = key + "=" + value;
            byte[] commentBytes = comment.getBytes(StandardCharsets.UTF_8);
            writeLittleEndianInt32(buffer, commentBytes.length);
            buffer.write(commentBytes, 0, commentBytes.length);
        }

        // Framing Bit (1) für OGG
        buffer.write(1);

        return buffer.toByteArray();
    }

    private void writeLittleEndianInt32(ByteArrayOutputStream buffer, int value) {
        buffer.write(value & 0xFF);
        buffer.write((value >> 8) & 0xFF);
        buffer.write((value >> 16) & 0xFF);
        buffer.write((value >> 24) & 0xFF);
    }
}
