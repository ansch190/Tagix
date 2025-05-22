package com.schwanitz.strategies.parsing;

import com.schwanitz.interfaces.FieldHandler;
import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.ID3Metadata;
import com.schwanitz.others.MetadataField;
import com.schwanitz.others.TextFieldHandler;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class ID3ParsingStrategy implements TagParsingStrategy {
    private static final Logger LOGGER = Logger.getLogger(ID3ParsingStrategy.class.getName());

    // ID3v2 Text Encoding
    private static final int ISO_8859_1 = 0;
    private static final int UTF_16 = 1;
    private static final int UTF_16BE = 2;
    private static final int UTF_8 = 3;

    // Genre-Mapping für ID3v1 (Standard + Winamp Extension)
    private static final String[] ID3V1_GENRES = {
            // Standard ID3v1 Genres (0-79)
            "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge",
            "Hip-Hop", "Jazz", "Metal", "New Age", "Oldies", "Other", "Pop", "R&B",
            "Rap", "Reggae", "Rock", "Techno", "Industrial", "Alternative", "Ska",
            "Death Metal", "Pranks", "Soundtrack", "Euro-Techno", "Ambient",
            "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion", "Trance", "Classical",
            "Instrumental", "Acid", "House", "Game", "Sound Clip", "Gospel",
            "Noise", "Alternative Rock", "Bass", "Soul", "Punk", "Space",
            "Meditative", "Instrumental Pop", "Instrumental Rock", "Ethnic",
            "Gothic", "Darkwave", "Techno-Industrial", "Electronic", "Pop-Folk",
            "Eurodance", "Dream", "Southern Rock", "Comedy", "Cult", "Gangsta",
            "Top 40", "Christian Rap", "Pop/Funk", "Jungle", "Native US",
            "Cabaret", "New Wave", "Psychadelic", "Rave", "Showtunes", "Trailer",
            "Lo-Fi", "Tribal", "Acid Punk", "Acid Jazz", "Polka", "Retro",
            "Musical", "Rock & Roll", "Hard Rock",

            // Winamp Extension (80-147)
            "Folk", "Folk-Rock", "National Folk", "Swing", "Fast Fusion",
            "Bebob", "Latin", "Revival", "Celtic", "Bluegrass", "Avantgarde",
            "Gothic Rock", "Progressive Rock", "Psychedelic Rock", "Symphonic Rock",
            "Slow Rock", "Big Band", "Chorus", "Easy Listening", "Acoustic",
            "Humour", "Speech", "Chanson", "Opera", "Chamber Music", "Sonata",
            "Symphony", "Booty Bass", "Primus", "Porn Groove", "Satire",
            "Slow Jam", "Club", "Tango", "Samba", "Folklore", "Ballad",
            "Power Ballad", "Rhythmic Soul", "Freestyle", "Duet", "Punk Rock",
            "Drum Solo", "A Capella", "Euro-House", "Dance Hall", "Goa",
            "Drum & Bass", "Club-House", "Hardcore", "Terror", "Indie",
            "BritPop", "Negerpunk", "Polsk Punk", "Beat", "Christian Gangsta Rap",
            "Heavy Metal", "Black Metal", "Crossover", "Contemporary Christian",
            "Christian Rock", "Merengue", "Salsa", "Thrash Metal", "Anime", "Jpop",
            "Synthpop", "Abstract", "Art Rock", "Baroque", "Bhangra", "Big Beat",
            "Breakbeat", "Chillout", "Downtempo", "Dub", "EBM", "Eclectic",
            "Electro", "Electroclash", "Emo", "Experimental", "Garage", "Global",
            "IDM", "Illbient", "Industro-Goth", "Jam Band", "Krautrock",
            "Leftfield", "Lounge", "Math Rock", "New Romantic", "Nu-Breakz",
            "Post-Punk", "Post-Rock", "Psytrance", "Shoegaze", "Space Rock",
            "Trop Rock", "World Music", "Neoclassical", "Audiobook", "Audio Theatre",
            "Neue Deutsche Welle", "Podcast", "Indie Rock", "G-Funk", "Dubstep",
            "Garage Rock", "Psybient"
    };

    private final Map<String, FieldHandler<?>> handlers;

    public ID3ParsingStrategy() {
        this.handlers = new HashMap<>();
        initializeDefaultHandlers();
    }

    @Override
    public boolean canHandle(TagFormat format) {
        return format == TagFormat.ID3V1 || format == TagFormat.ID3V1_1 ||
                format == TagFormat.ID3V2_2 || format == TagFormat.ID3V2_3 || format == TagFormat.ID3V2_4;
    }

    @Override
    public Metadata parseTag(TagFormat format, RandomAccessFile file, long offset, long size) throws IOException {
        TagInfo tagInfo = new TagInfo(format, offset, size);
        return parseID3Tag(file, tagInfo);
    }

    private void initializeDefaultHandlers() {
        // ID3v2 Standard-Frames
        handlers.put("TIT2", new TextFieldHandler("TIT2")); // Titel
        handlers.put("TPE1", new TextFieldHandler("TPE1")); // Künstler
        handlers.put("TALB", new TextFieldHandler("TALB")); // Album
        handlers.put("TYER", new TextFieldHandler("TYER")); // Jahr
        handlers.put("TCON", new TextFieldHandler("TCON")); // Genre
        handlers.put("TRCK", new TextFieldHandler("TRCK")); // Track
        handlers.put("TPE2", new TextFieldHandler("TPE2")); // Album-Künstler
        handlers.put("TPOS", new TextFieldHandler("TPOS")); // Disc Number
        handlers.put("COMM", new TextFieldHandler("COMM")); // Kommentar

        // ID3v2.2 3-Zeichen-Frames
        handlers.put("TT2", new TextFieldHandler("TT2"));   // Titel (v2.2)
        handlers.put("TP1", new TextFieldHandler("TP1"));   // Künstler (v2.2)
        handlers.put("TAL", new TextFieldHandler("TAL"));   // Album (v2.2)
        handlers.put("TYE", new TextFieldHandler("TYE"));   // Jahr (v2.2)
        handlers.put("TCO", new TextFieldHandler("TCO"));   // Genre (v2.2)
        handlers.put("TRK", new TextFieldHandler("TRK"));   // Track (v2.2)
        handlers.put("COM", new TextFieldHandler("COM"));   // Kommentar (v2.2)
    }

    public void registerHandler(String key, FieldHandler<?> handler) {
        handlers.put(key, handler);
    }

    private Metadata parseID3Tag(RandomAccessFile file, TagInfo tagInfo) throws IOException {
        TagFormat format = tagInfo.getFormat();
        long offset = tagInfo.getOffset();
        long size = tagInfo.getSize();

        ID3Metadata metadata = new ID3Metadata(format);

        switch (format) {
            case ID3V1:
            case ID3V1_1:
                parseID3v1(file, metadata, offset, format);
                break;
            case ID3V2_2:
            case ID3V2_3:
            case ID3V2_4:
                parseID3v2(file, metadata, offset, size, format);
                break;
            default:
                throw new IllegalArgumentException("Unsupported ID3 format: " + format);
        }

        return metadata;
    }

    private void parseID3v1(RandomAccessFile file, ID3Metadata metadata, long offset, TagFormat format)
            throws IOException {
        file.seek(offset);
        byte[] tagData = new byte[128];
        int bytesRead = file.read(tagData);

        if (bytesRead != 128) {
            throw new IOException("Could not read complete ID3v1 tag");
        }

        // TAG-Header prüfen
        String header = new String(tagData, 0, 3, StandardCharsets.ISO_8859_1);
        if (!"TAG".equals(header)) {
            throw new IOException("Invalid ID3v1 header");
        }

        // Titel (30 Bytes)
        String title = extractFixedString(tagData, 3, 30);
        if (!title.isEmpty()) {
            addField(metadata, "TIT2", title);
        }

        // Künstler (30 Bytes)
        String artist = extractFixedString(tagData, 33, 30);
        if (!artist.isEmpty()) {
            addField(metadata, "TPE1", artist);
        }

        // Album (30 Bytes)
        String album = extractFixedString(tagData, 63, 30);
        if (!album.isEmpty()) {
            addField(metadata, "TALB", album);
        }

        // Jahr (4 Bytes)
        String year = extractFixedString(tagData, 93, 4);
        if (!year.isEmpty()) {
            addField(metadata, "TYER", year);
        }

        // Kommentar (28 oder 30 Bytes je nach Version)
        if (format == TagFormat.ID3V1_1) {
            // ID3v1.1: Kommentar (28 Bytes) + Track (1 Byte)
            String comment = extractFixedString(tagData, 97, 28);
            if (!comment.isEmpty()) {
                addField(metadata, "COMM", comment);
            }

            // Track-Nummer
            int track = tagData[126] & 0xFF;
            if (track > 0) {
                addField(metadata, "TRCK", String.valueOf(track));
            }
        } else {
            // ID3v1: Kommentar (30 Bytes)
            String comment = extractFixedString(tagData, 97, 30);
            if (!comment.isEmpty()) {
                addField(metadata, "COMM", comment);
            }
        }

        // Genre
        int genreIndex = tagData[127] & 0xFF;
        if (genreIndex < ID3V1_GENRES.length) {
            addField(metadata, "TCON", ID3V1_GENRES[genreIndex]);
        }
    }

    private void parseID3v2(RandomAccessFile file, ID3Metadata metadata, long offset, long size, TagFormat format)
            throws IOException {
        file.seek(offset);

        // ID3v2-Header lesen (10 Bytes)
        byte[] header = new byte[10];
        file.read(header);

        // Header validieren
        if (!"ID3".equals(new String(header, 0, 3))) {
            throw new IOException("Invalid ID3v2 header");
        }

        int majorVersion = header[3] & 0xFF;
        int flags = header[5] & 0xFF;

        // Größe extrahieren (synchsafe integer)
        int tagSize = ((header[6] & 0x7F) << 21) | ((header[7] & 0x7F) << 14) |
                ((header[8] & 0x7F) << 7) | (header[9] & 0x7F);

        boolean hasExtendedHeader = (flags & 0x40) != 0;
        long frameDataStart = offset + 10;

        // Extended Header überspringen, falls vorhanden
        if (hasExtendedHeader) {
            file.seek(frameDataStart);
            byte[] extHeaderSize = new byte[4];
            file.read(extHeaderSize);

            int extSize;
            if (majorVersion == 3) {
                // ID3v2.3: normale 32-bit Größe
                extSize = ((extHeaderSize[0] & 0xFF) << 24) | ((extHeaderSize[1] & 0xFF) << 16) |
                        ((extHeaderSize[2] & 0xFF) << 8) | (extHeaderSize[3] & 0xFF);
            } else {
                // ID3v2.4: synchsafe integer
                extSize = ((extHeaderSize[0] & 0x7F) << 21) | ((extHeaderSize[1] & 0x7F) << 14) |
                        ((extHeaderSize[2] & 0x7F) << 7) | (extHeaderSize[3] & 0x7F);
            }
            frameDataStart += extSize;
        }

        // Frames parsen
        long currentPos = frameDataStart;
        long endPos = offset + 10 + tagSize;

        while (currentPos < endPos - 10) { // Mindestens 10 Bytes für Frame-Header
            file.seek(currentPos);

            int frameHeaderSize = (majorVersion == 2) ? 6 : 10;
            byte[] frameHeader = new byte[frameHeaderSize];
            int bytesRead = file.read(frameHeader);

            if (bytesRead != frameHeaderSize) {
                break;
            }

            String frameId;
            int frameSize;
            int frameFlags = 0;

            if (majorVersion == 2) {
                // ID3v2.2: 3 Zeichen Frame-ID + 3 Bytes Größe
                frameId = new String(frameHeader, 0, 3);
                frameSize = ((frameHeader[3] & 0xFF) << 16) | ((frameHeader[4] & 0xFF) << 8) |
                        (frameHeader[5] & 0xFF);
            } else {
                // ID3v2.3/2.4: 4 Zeichen Frame-ID + 4 Bytes Größe + 2 Bytes Flags
                frameId = new String(frameHeader, 0, 4);

                if (majorVersion == 4) {
                    // ID3v2.4: synchsafe integer
                    frameSize = ((frameHeader[4] & 0x7F) << 21) | ((frameHeader[5] & 0x7F) << 14) |
                            ((frameHeader[6] & 0x7F) << 7) | (frameHeader[7] & 0x7F);
                } else {
                    // ID3v2.3: normale 32-bit Größe
                    frameSize = ((frameHeader[4] & 0xFF) << 24) | ((frameHeader[5] & 0xFF) << 16) |
                            ((frameHeader[6] & 0xFF) << 8) | (frameHeader[7] & 0xFF);
                }
                frameFlags = ((frameHeader[8] & 0xFF) << 8) | (frameHeader[9] & 0xFF);
            }

            // Ungültige oder leere Frame-ID -> Ende des Tag-Bereichs
            if (frameId.contains("\0") || frameSize <= 0 || frameSize > endPos - currentPos) {
                break;
            }

            // Frame-Daten lesen
            byte[] frameData = new byte[frameSize];
            file.read(frameData);

            // Frame parsen
            parseFrame(metadata, frameId, frameData, majorVersion);

            currentPos += frameHeaderSize + frameSize;
        }
    }

    private void parseFrame(ID3Metadata metadata, String frameId, byte[] frameData, int majorVersion) {
        try {
            if (frameData.length == 0) {
                return;
            }

            // Text-Frames (beginnen mit T, außer TXXX)
            if (frameId.startsWith("T") && !frameId.equals("TXXX")) {
                String text = extractTextFromFrame(frameData);
                if (!text.isEmpty()) {
                    addField(metadata, frameId, text);
                }
            }
            // Comment-Frames
            else if (frameId.startsWith("COMM") || frameId.equals("COM")) {
                String comment = extractCommentFromFrame(frameData);
                if (!comment.isEmpty()) {
                    addField(metadata, frameId, comment);
                }
            }
            // Weitere Frame-Typen können hier hinzugefügt werden
            else {
                LOGGER.fine("Unhandled frame type: " + frameId);
            }
        } catch (Exception e) {
            LOGGER.warning("Error parsing frame " + frameId + ": " + e.getMessage());
        }
    }

    private String extractTextFromFrame(byte[] frameData) {
        if (frameData.length < 1) {
            return "";
        }

        int encoding = frameData[0] & 0xFF;
        byte[] textData = new byte[frameData.length - 1];
        System.arraycopy(frameData, 1, textData, 0, textData.length);

        return decodeText(textData, encoding);
    }

    private String extractCommentFromFrame(byte[] frameData) {
        if (frameData.length < 4) {
            return "";
        }

        int encoding = frameData[0] & 0xFF;
        // Sprache (3 Bytes) überspringen
        int textStart = 4;

        // Content Descriptor (null-terminated) überspringen
        while (textStart < frameData.length && frameData[textStart] != 0) {
            textStart++;
        }
        textStart++; // Null-Terminator überspringen

        if (textStart >= frameData.length) {
            return "";
        }

        byte[] commentData = new byte[frameData.length - textStart];
        System.arraycopy(frameData, textStart, commentData, 0, commentData.length);

        return decodeText(commentData, encoding);
    }

    private String decodeText(byte[] data, int encoding) {
        if (data.length == 0) {
            return "";
        }

        Charset charset;
        switch (encoding) {
            case ISO_8859_1:
                charset = StandardCharsets.ISO_8859_1;
                break;
            case UTF_16:
                charset = StandardCharsets.UTF_16;
                break;
            case UTF_16BE:
                charset = StandardCharsets.UTF_16BE;
                break;
            case UTF_8:
                charset = StandardCharsets.UTF_8;
                break;
            default:
                charset = StandardCharsets.ISO_8859_1;
                break;
        }

        String result = new String(data, charset).trim();

        // Null-Terminatoren entfernen
        int nullPos = result.indexOf('\0');
        if (nullPos >= 0) {
            result = result.substring(0, nullPos);
        }

        return result;
    }

    private String extractFixedString(byte[] data, int offset, int length) {
        if (offset + length > data.length) {
            return "";
        }

        // Null-Terminator suchen
        int actualLength = length;
        for (int i = 0; i < length; i++) {
            if (data[offset + i] == 0) {
                actualLength = i;
                break;
            }
        }

        if (actualLength == 0) {
            return "";
        }

        return new String(data, offset, actualLength, StandardCharsets.ISO_8859_1).trim();
    }

    @SuppressWarnings("unchecked")
    private void addField(ID3Metadata metadata, String key, String value) {
        FieldHandler<?> handler = handlers.get(key);
        if (handler != null) {
            metadata.addField(new MetadataField<>(key, value, (FieldHandler<String>) handler));
        } else {
            // Fallback: TextFieldHandler erstellen
            TextFieldHandler textHandler = new TextFieldHandler(key);
            metadata.addField(new MetadataField<>(key, value, textHandler));
        }
    }
}