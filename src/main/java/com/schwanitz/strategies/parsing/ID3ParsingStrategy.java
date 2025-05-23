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
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        // ID3v2.3/2.4 Standard-Frames (4 Zeichen) - VOLLSTÄNDIG

        // Text Information Frames (T*)
        handlers.put("TIT1", new TextFieldHandler("TIT1")); // Content Group Description
        handlers.put("TIT2", new TextFieldHandler("TIT2")); // Title/Song name
        handlers.put("TIT3", new TextFieldHandler("TIT3")); // Subtitle/Description refinement
        handlers.put("TALB", new TextFieldHandler("TALB")); // Album/Movie/Show title
        handlers.put("TOAL", new TextFieldHandler("TOAL")); // Original album/movie/show title
        handlers.put("TRCK", new TextFieldHandler("TRCK")); // Track number/Position in set
        handlers.put("TPOS", new TextFieldHandler("TPOS")); // Part of a set
        handlers.put("TSST", new TextFieldHandler("TSST")); // Set subtitle
        handlers.put("TSRC", new TextFieldHandler("TSRC")); // ISRC (International Standard Recording Code)

        // People Frames
        handlers.put("TPE1", new TextFieldHandler("TPE1")); // Lead performer(s)/Soloist(s)
        handlers.put("TPE2", new TextFieldHandler("TPE2")); // Band/orchestra/accompaniment
        handlers.put("TPE3", new TextFieldHandler("TPE3")); // Conductor/performer refinement
        handlers.put("TPE4", new TextFieldHandler("TPE4")); // Interpreted, remixed, modified by
        handlers.put("TOPE", new TextFieldHandler("TOPE")); // Original artist(s)/performer(s)
        handlers.put("TEXT", new TextFieldHandler("TEXT")); // Lyricist/Text writer
        handlers.put("TOLY", new TextFieldHandler("TOLY")); // Original lyricist(s)/text writer(s)
        handlers.put("TCOM", new TextFieldHandler("TCOM")); // Composer
        handlers.put("TMCL", new TextFieldHandler("TMCL")); // Musician credits list
        handlers.put("TIPL", new TextFieldHandler("TIPL")); // Involved people list
        handlers.put("TENC", new TextFieldHandler("TENC")); // Encoded by

        // Copyright/Legal
        handlers.put("TCOP", new TextFieldHandler("TCOP")); // Copyright message
        handlers.put("TPRO", new TextFieldHandler("TPRO")); // Produced notice
        handlers.put("TPUB", new TextFieldHandler("TPUB")); // Publisher
        handlers.put("TOWN", new TextFieldHandler("TOWN")); // File owner/licensee
        handlers.put("TRSN", new TextFieldHandler("TRSN")); // Internet radio station name
        handlers.put("TRSO", new TextFieldHandler("TRSO")); // Internet radio station owner

        // Time/Date Frames
        handlers.put("TYER", new TextFieldHandler("TYER")); // Year (ID3v2.3)
        handlers.put("TDAT", new TextFieldHandler("TDAT")); // Date (ID3v2.3)
        handlers.put("TIME", new TextFieldHandler("TIME")); // Time (ID3v2.3)
        handlers.put("TORY", new TextFieldHandler("TORY")); // Original release year (ID3v2.3)
        handlers.put("TRDA", new TextFieldHandler("TRDA")); // Recording dates (ID3v2.3)
        handlers.put("TDRC", new TextFieldHandler("TDRC")); // Recording time (ID3v2.4)
        handlers.put("TDRL", new TextFieldHandler("TDRL")); // Release time (ID3v2.4)
        handlers.put("TDOR", new TextFieldHandler("TDOR")); // Original release time (ID3v2.4)
        handlers.put("TDTG", new TextFieldHandler("TDTG")); // Tagging time (ID3v2.4)

        // Genre/Style
        handlers.put("TCON", new TextFieldHandler("TCON")); // Content type (Genre)
        handlers.put("TCAT", new TextFieldHandler("TCAT")); // Podcast category
        handlers.put("TKWD", new TextFieldHandler("TKWD")); // Podcast keywords
        handlers.put("TDES", new TextFieldHandler("TDES")); // Podcast description

        // Technical/Audio
        handlers.put("TBPM", new TextFieldHandler("TBPM")); // BPM (beats per minute)
        handlers.put("TKEY", new TextFieldHandler("TKEY")); // Initial key
        handlers.put("TLAN", new TextFieldHandler("TLAN")); // Language(s)
        handlers.put("TLEN", new TextFieldHandler("TLEN")); // Length
        handlers.put("TMED", new TextFieldHandler("TMED")); // Media type
        handlers.put("TFLT", new TextFieldHandler("TFLT")); // File type
        handlers.put("TSSE", new TextFieldHandler("TSSE")); // Software/Hardware and settings used for encoding

        // Mood/Style
        handlers.put("TMOO", new TextFieldHandler("TMOO")); // Mood
        handlers.put("TGID", new TextFieldHandler("TGID")); // Podcast ID

        // Sort Order Frames
        handlers.put("TSOA", new TextFieldHandler("TSOA")); // Album sort order
        handlers.put("TSOP", new TextFieldHandler("TSOP")); // Performer sort order
        handlers.put("TSOT", new TextFieldHandler("TSOT")); // Title sort order

        // User Defined
        handlers.put("TXXX", new TextFieldHandler("TXXX")); // User defined text information frame

        // Comment Frames
        handlers.put("COMM", new TextFieldHandler("COMM")); // Comments

        // Lyrics
        handlers.put("USLT", new TextFieldHandler("USLT")); // Unsynchronised lyric/text transcription
        handlers.put("SYLT", new TextFieldHandler("SYLT")); // Synchronised lyric/text

        // URL Frames (W*)
        handlers.put("WCOM", new TextFieldHandler("WCOM")); // Commercial information
        handlers.put("WCOP", new TextFieldHandler("WCOP")); // Copyright/Legal information
        handlers.put("WOAF", new TextFieldHandler("WOAF")); // Official audio file webpage
        handlers.put("WOAR", new TextFieldHandler("WOAR")); // Official artist/performer webpage
        handlers.put("WOAS", new TextFieldHandler("WOAS")); // Official audio source webpage
        handlers.put("WORS", new TextFieldHandler("WORS")); // Official internet radio station homepage
        handlers.put("WPAY", new TextFieldHandler("WPAY")); // Payment
        handlers.put("WPUB", new TextFieldHandler("WPUB")); // Publishers official webpage
        handlers.put("WXXX", new TextFieldHandler("WXXX")); // User defined URL link frame

        // Binary/Special Frames
        handlers.put("APIC", new TextFieldHandler("APIC")); // Attached picture
        handlers.put("GEOB", new TextFieldHandler("GEOB")); // General encapsulated object
        handlers.put("PCNT", new TextFieldHandler("PCNT")); // Play counter
        handlers.put("POPM", new TextFieldHandler("POPM")); // Popularimeter
        handlers.put("RBUF", new TextFieldHandler("RBUF")); // Recommended buffer size
        handlers.put("AENC", new TextFieldHandler("AENC")); // Audio encryption
        handlers.put("LINK", new TextFieldHandler("LINK")); // Linked information
        handlers.put("POSS", new TextFieldHandler("POSS")); // Position synchronisation frame
        handlers.put("USER", new TextFieldHandler("USER")); // Terms of use
        handlers.put("OWNE", new TextFieldHandler("OWNE")); // Ownership frame
        handlers.put("PRIV", new TextFieldHandler("PRIV")); // Private frame

        // Music-specific
        handlers.put("MVNM", new TextFieldHandler("MVNM")); // Movement Name
        handlers.put("MVIN", new TextFieldHandler("MVIN")); // Movement Number
        handlers.put("GRP1", new TextFieldHandler("GRP1")); // Grouping

        // ID3v2.2 3-Zeichen-Frames (Backward Compatibility)
        handlers.put("TT1", new TextFieldHandler("TT1"));   // Content Group (v2.2)
        handlers.put("TT2", new TextFieldHandler("TT2"));   // Title (v2.2)
        handlers.put("TT3", new TextFieldHandler("TT3"));   // Subtitle (v2.2)
        handlers.put("TP1", new TextFieldHandler("TP1"));   // Artist (v2.2)
        handlers.put("TP2", new TextFieldHandler("TP2"));   // Band (v2.2)
        handlers.put("TP3", new TextFieldHandler("TP3"));   // Conductor (v2.2)
        handlers.put("TP4", new TextFieldHandler("TP4"));   // Interpreted by (v2.2)
        handlers.put("TAL", new TextFieldHandler("TAL"));   // Album (v2.2)
        handlers.put("TYE", new TextFieldHandler("TYE"));   // Year (v2.2)
        handlers.put("TDA", new TextFieldHandler("TDA"));   // Date (v2.2)
        handlers.put("TIM", new TextFieldHandler("TIM"));   // Time (v2.2)
        handlers.put("TRD", new TextFieldHandler("TRD"));   // Recording dates (v2.2)
        handlers.put("TCO", new TextFieldHandler("TCO"));   // Genre (v2.2)
        handlers.put("TRK", new TextFieldHandler("TRK"));   // Track (v2.2)
        handlers.put("TPA", new TextFieldHandler("TPA"));   // Part of set (v2.2)
        handlers.put("TCR", new TextFieldHandler("TCR"));   // Copyright (v2.2)
        handlers.put("TPB", new TextFieldHandler("TPB"));   // Publisher (v2.2)
        handlers.put("TEN", new TextFieldHandler("TEN"));   // Encoded by (v2.2)
        handlers.put("TSS", new TextFieldHandler("TSS"));   // Software settings (v2.2)
        handlers.put("TBP", new TextFieldHandler("TBP"));   // BPM (v2.2)
        handlers.put("TCM", new TextFieldHandler("TCM"));   // Composer (v2.2)
        handlers.put("TKE", new TextFieldHandler("TKE"));   // Initial key (v2.2)
        handlers.put("TLA", new TextFieldHandler("TLA"));   // Language (v2.2)
        handlers.put("TLE", new TextFieldHandler("TLE"));   // Length (v2.2)
        handlers.put("TMT", new TextFieldHandler("TMT"));   // Media type (v2.2)
        handlers.put("TOF", new TextFieldHandler("TOF"));   // Original filename (v2.2)
        handlers.put("TOL", new TextFieldHandler("TOL"));   // Original lyricist (v2.2)
        handlers.put("TOA", new TextFieldHandler("TOA"));   // Original artist (v2.2)
        handlers.put("TOT", new TextFieldHandler("TOT"));   // Original album title (v2.2)
        handlers.put("TOR", new TextFieldHandler("TOR"));   // Original release year (v2.2)
        handlers.put("TXT", new TextFieldHandler("TXT"));   // Lyricist (v2.2)
        handlers.put("TXX", new TextFieldHandler("TXX"));   // User defined text (v2.2)
        handlers.put("COM", new TextFieldHandler("COM"));   // Comment (v2.2)
        handlers.put("ULT", new TextFieldHandler("ULT"));   // Unsynchronised lyrics (v2.2)
        handlers.put("WAF", new TextFieldHandler("WAF"));   // Official audio file webpage (v2.2)
        handlers.put("WAR", new TextFieldHandler("WAR"));   // Official artist webpage (v2.2)
        handlers.put("WAS", new TextFieldHandler("WAS"));   // Official audio source webpage (v2.2)
        handlers.put("WCM", new TextFieldHandler("WCM"));   // Commercial information (v2.2)
        handlers.put("WCP", new TextFieldHandler("WCP"));   // Copyright information (v2.2)
        handlers.put("WPB", new TextFieldHandler("WPB"));   // Publishers webpage (v2.2)
        handlers.put("WXX", new TextFieldHandler("WXX"));   // User defined URL (v2.2)
        handlers.put("PIC", new TextFieldHandler("PIC"));   // Attached picture (v2.2)
        handlers.put("GEO", new TextFieldHandler("GEO"));   // General encapsulated object (v2.2)
        handlers.put("CNT", new TextFieldHandler("CNT"));   // Play counter (v2.2)
        handlers.put("POP", new TextFieldHandler("POP"));   // Popularimeter (v2.2)
        handlers.put("BUF", new TextFieldHandler("BUF"));   // Recommended buffer size (v2.2)
        handlers.put("CRA", new TextFieldHandler("CRA"));   // Audio encryption (v2.2)
        handlers.put("LNK", new TextFieldHandler("LNK"));   // Linked information (v2.2)
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

        LOGGER.fine("Successfully parsed ID3v1" + (format == TagFormat.ID3V1_1 ? ".1" : "") + " tag");
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

        LOGGER.fine("Successfully parsed ID3v2." + majorVersion + " tag");
    }

    private void parseFrame(ID3Metadata metadata, String frameId, byte[] frameData, int majorVersion) {
        try {
            if (frameData.length == 0) {
                return;
            }

            // Text-Frames (beginnen mit T, außer TXXX)
            if (frameId.startsWith("T") && !frameId.equals("TXXX") && !frameId.equals("TXX")) {
                String text = extractTextFromFrame(frameData, majorVersion);
                if (!text.isEmpty()) {
                    // Spezielle Behandlung für Genre (TCON/TCO)
                    if ("TCON".equals(frameId) || "TCO".equals(frameId)) {
                        text = parseGenre(text);
                    }
                    addField(metadata, frameId, text);
                }
            }
            // User Defined Text Frames (TXXX/TXX)
            else if ("TXXX".equals(frameId) || "TXX".equals(frameId)) {
                String userText = extractUserDefinedTextFrame(frameData, majorVersion);
                if (!userText.isEmpty()) {
                    addField(metadata, frameId, userText);
                }
            }
            // Comment-Frames
            else if (frameId.startsWith("COMM") || frameId.equals("COM")) {
                String comment = extractCommentFromFrame(frameData, majorVersion);
                if (!comment.isEmpty()) {
                    addField(metadata, frameId, comment);
                }
            }
            // Unsynchronized Lyrics
            else if ("USLT".equals(frameId) || "ULT".equals(frameId)) {
                String lyrics = extractLyricsFromFrame(frameData, majorVersion);
                if (!lyrics.isEmpty()) {
                    addField(metadata, frameId, lyrics);
                }
            }
            // URL Frames (beginnen mit W)
            else if (frameId.startsWith("W")) {
                String url = extractUrlFromFrame(frameData, frameId, majorVersion);
                if (!url.isEmpty()) {
                    addField(metadata, frameId, url);
                }
            }
            // Attached Picture
            else if ("APIC".equals(frameId) || "PIC".equals(frameId)) {
                String pictureInfo = extractPictureFromFrame(frameData, frameId, majorVersion);
                if (!pictureInfo.isEmpty()) {
                    addField(metadata, frameId, pictureInfo);
                }
            }
            // Play Counter
            else if ("PCNT".equals(frameId) || "CNT".equals(frameId)) {
                String playCount = extractPlayCountFromFrame(frameData);
                if (!playCount.isEmpty()) {
                    addField(metadata, frameId, playCount);
                }
            }
            // Popularimeter
            else if ("POPM".equals(frameId) || "POP".equals(frameId)) {
                String rating = extractPopularimeterFromFrame(frameData);
                if (!rating.isEmpty()) {
                    addField(metadata, frameId, rating);
                }
            }
            // General Encapsulated Object
            else if ("GEOB".equals(frameId) || "GEO".equals(frameId)) {
                String objectInfo = extractGeneralObjectFromFrame(frameData, majorVersion);
                if (!objectInfo.isEmpty()) {
                    addField(metadata, frameId, objectInfo);
                }
            }
            // Private Frame
            else if ("PRIV".equals(frameId)) {
                String privateInfo = extractPrivateFromFrame(frameData);
                if (!privateInfo.isEmpty()) {
                    addField(metadata, frameId, privateInfo);
                }
            }
            // Weitere Frame-Typen können hier hinzugefügt werden
            else {
                LOGGER.fine("Unhandled frame type: " + frameId);
                // Versuche als Text-Frame zu behandeln (Fallback)
                try {
                    String text = extractTextFromFrame(frameData, majorVersion);
                    if (!text.isEmpty()) {
                        addField(metadata, frameId, text);
                        LOGGER.fine("Treated unknown frame " + frameId + " as text frame");
                    }
                } catch (Exception e) {
                    LOGGER.fine("Could not parse unknown frame " + frameId + " as text: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error parsing frame " + frameId + ": " + e.getMessage());
        }
    }

    private String extractTextFromFrame(byte[] frameData, int majorVersion) {
        if (frameData.length < 1) {
            return "";
        }

        int encoding = frameData[0] & 0xFF;
        byte[] textData = new byte[frameData.length - 1];
        System.arraycopy(frameData, 1, textData, 0, textData.length);

        return decodeText(textData, encoding, majorVersion);
    }

    private String extractUserDefinedTextFrame(byte[] frameData, int majorVersion) {
        if (frameData.length < 2) {
            return "";
        }

        int encoding = frameData[0] & 0xFF;

        // Finde null-terminated description
        int descEnd = findNullTerminator(frameData, 1, encoding);
        if (descEnd == -1) {
            return "";
        }

        // Description extrahieren
        byte[] descData = new byte[descEnd - 1];
        System.arraycopy(frameData, 1, descData, 0, descData.length);
        String description = decodeText(descData, encoding, majorVersion);

        // Value extrahieren (nach null-terminator)
        int valueStart = descEnd + getNullTerminatorSize(encoding);
        if (valueStart >= frameData.length) {
            return description; // Nur Description, kein Value
        }

        byte[] valueData = new byte[frameData.length - valueStart];
        System.arraycopy(frameData, valueStart, valueData, 0, valueData.length);
        String value = decodeText(valueData, encoding, majorVersion);

        // Format: "Description: Value" oder nur Value wenn Description leer
        if (description.isEmpty()) {
            return value;
        } else {
            return description + ": " + value;
        }
    }

    private String extractCommentFromFrame(byte[] frameData, int majorVersion) {
        if (frameData.length < 4) {
            return "";
        }

        int encoding = frameData[0] & 0xFF;
        // Sprache (3 Bytes) überspringen
        int textStart = 4;

        // Content Descriptor (null-terminated) überspringen
        int descEnd = findNullTerminator(frameData, textStart, encoding);
        if (descEnd != -1) {
            textStart = descEnd + getNullTerminatorSize(encoding);
        }

        if (textStart >= frameData.length) {
            return "";
        }

        byte[] commentData = new byte[frameData.length - textStart];
        System.arraycopy(frameData, textStart, commentData, 0, commentData.length);

        return decodeText(commentData, encoding, majorVersion);
    }

    private String extractLyricsFromFrame(byte[] frameData, int majorVersion) {
        if (frameData.length < 4) {
            return "";
        }

        int encoding = frameData[0] & 0xFF;
        // Sprache (3 Bytes) überspringen
        int textStart = 4;

        // Content Descriptor (null-terminated) überspringen
        int descEnd = findNullTerminator(frameData, textStart, encoding);
        if (descEnd != -1) {
            textStart = descEnd + getNullTerminatorSize(encoding);
        }

        if (textStart >= frameData.length) {
            return "";
        }

        byte[] lyricsData = new byte[frameData.length - textStart];
        System.arraycopy(frameData, textStart, lyricsData, 0, lyricsData.length);

        return decodeText(lyricsData, encoding, majorVersion);
    }

    private String extractUrlFromFrame(byte[] frameData, String frameId, int majorVersion) {
        // URL-Frames sind meist ASCII/ISO-8859-1
        if ("WXXX".equals(frameId) || "WXX".equals(frameId)) {
            // User-defined URL hat Encoding + Description + URL
            if (frameData.length < 2) {
                return "";
            }

            int encoding = frameData[0] & 0xFF;
            int descEnd = findNullTerminator(frameData, 1, encoding);
            if (descEnd == -1) {
                return "";
            }

            // Description extrahieren
            byte[] descData = new byte[descEnd - 1];
            System.arraycopy(frameData, 1, descData, 0, descData.length);
            String description = decodeText(descData, encoding, majorVersion);

            // URL extrahieren (nach null-terminator, meist ISO-8859-1)
            int urlStart = descEnd + getNullTerminatorSize(encoding);
            if (urlStart >= frameData.length) {
                return description;
            }

            byte[] urlData = new byte[frameData.length - urlStart];
            System.arraycopy(frameData, urlStart, urlData, 0, urlData.length);
            String url = new String(urlData, StandardCharsets.ISO_8859_1).trim();

            return description.isEmpty() ? url : description + ": " + url;
        } else {
            // Standard URL-Frames sind direkt ISO-8859-1
            return new String(frameData, StandardCharsets.ISO_8859_1).trim();
        }
    }

    private String extractPictureFromFrame(byte[] frameData, String frameId, int majorVersion) {
        if (frameData.length < 2) {
            return "";
        }

        try {
            int pos = 0;
            int encoding = frameData[pos++] & 0xFF;

            if ("APIC".equals(frameId)) {
                // ID3v2.3/2.4: MIME type (null-terminated)
                int mimeEnd = findNullTerminator(frameData, pos, 0); // MIME ist immer ISO-8859-1
                if (mimeEnd == -1) {
                    return "[PICTURE: Invalid MIME type]";
                }

                String mimeType = new String(frameData, pos, mimeEnd - pos, StandardCharsets.ISO_8859_1);
                pos = mimeEnd + 1;

                if (pos >= frameData.length) {
                    return "[PICTURE: " + mimeType + "]";
                }

                // Picture type (1 byte)
                int pictureType = frameData[pos++] & 0xFF;
                String pictureTypeStr = getPictureTypeDescription(pictureType);

                // Description (null-terminated)
                int descEnd = findNullTerminator(frameData, pos, encoding);
                String description = "";
                if (descEnd != -1) {
                    byte[] descData = new byte[descEnd - pos];
                    System.arraycopy(frameData, pos, descData, 0, descData.length);
                    description = decodeText(descData, encoding, majorVersion);
                    pos = descEnd + getNullTerminatorSize(encoding);
                }

                // Picture data
                int pictureDataSize = frameData.length - pos;
                if (pictureDataSize > 0) {
                    // Für Cover Art: Base64-Encoding der ersten 100 Bytes für Preview
                    int previewSize = Math.min(pictureDataSize, 100);
                    byte[] previewData = new byte[previewSize];
                    System.arraycopy(frameData, pos, previewData, 0, previewSize);
                    String base64Preview = Base64.getEncoder().encodeToString(previewData);

                    String result = "[PICTURE:" + mimeType + "," + pictureTypeStr + "," + pictureDataSize + " bytes";
                    if (!description.isEmpty()) {
                        result += ",desc:" + description;
                    }
                    result += ",preview:" + base64Preview;
                    if (previewSize < pictureDataSize) {
                        result += "...";
                    }
                    result += "]";
                    return result;
                }

                return "[PICTURE:" + mimeType + "," + pictureTypeStr + ",no data]";

            } else if ("PIC".equals(frameId)) {
                // ID3v2.2: Image format (3 bytes) + Picture type + Description + Picture data
                if (frameData.length < 5) {
                    return "[PICTURE: Invalid PIC frame]";
                }

                String imageFormat = new String(frameData, pos, 3, StandardCharsets.ISO_8859_1);
                pos += 3;

                int pictureType = frameData[pos++] & 0xFF;
                String pictureTypeStr = getPictureTypeDescription(pictureType);

                // Description (null-terminated)
                int descEnd = findNullTerminator(frameData, pos, encoding);
                String description = "";
                if (descEnd != -1) {
                    byte[] descData = new byte[descEnd - pos];
                    System.arraycopy(frameData, pos, descData, 0, descData.length);
                    description = decodeText(descData, encoding, majorVersion);
                    pos = descEnd + getNullTerminatorSize(encoding);
                }

                int pictureDataSize = frameData.length - pos;
                if (pictureDataSize > 0) {
                    int previewSize = Math.min(pictureDataSize, 100);
                    byte[] previewData = new byte[previewSize];
                    System.arraycopy(frameData, pos, previewData, 0, previewSize);
                    String base64Preview = Base64.getEncoder().encodeToString(previewData);

                    String result = "[PICTURE:" + imageFormat + "," + pictureTypeStr + "," + pictureDataSize + " bytes";
                    if (!description.isEmpty()) {
                        result += ",desc:" + description;
                    }
                    result += ",preview:" + base64Preview;
                    if (previewSize < pictureDataSize) {
                        result += "...";
                    }
                    result += "]";
                    return result;
                }

                return "[PICTURE:" + imageFormat + "," + pictureTypeStr + ",no data]";
            }

        } catch (Exception e) {
            LOGGER.warning("Error parsing picture frame: " + e.getMessage());
        }

        return "[PICTURE:" + frameData.length + " bytes]";
    }

    private String extractPlayCountFromFrame(byte[] frameData) {
        if (frameData.length == 0) {
            return "";
        }

        // Play count ist ein big-endian integer (variable Länge)
        long playCount = 0;
        for (int i = 0; i < Math.min(frameData.length, 8); i++) {
            playCount = (playCount << 8) | (frameData[i] & 0xFF);
        }

        return String.valueOf(playCount);
    }

    private String extractPopularimeterFromFrame(byte[] frameData) {
        if (frameData.length < 2) {
            return "";
        }

        // Email (null-terminated) + Rating (1 byte) + Counter (variable)
        int emailEnd = findNullTerminator(frameData, 0, 0); // Email ist ISO-8859-1
        if (emailEnd == -1) {
            return "";
        }

        String email = new String(frameData, 0, emailEnd, StandardCharsets.ISO_8859_1);
        int pos = emailEnd + 1;

        if (pos >= frameData.length) {
            return email;
        }

        int rating = frameData[pos++] & 0xFF;

        // Counter (optional)
        long counter = 0;
        if (pos < frameData.length) {
            for (int i = pos; i < Math.min(frameData.length, pos + 8); i++) {
                counter = (counter << 8) | (frameData[i] & 0xFF);
            }
        }

        String result = "Rating:" + rating + "/255";
        if (!email.isEmpty()) {
            result = email + "," + result;
        }
        if (counter > 0) {
            result += ",Count:" + counter;
        }

        return result;
    }

    private String extractGeneralObjectFromFrame(byte[] frameData, int majorVersion) {
        if (frameData.length < 2) {
            return "";
        }

        int encoding = frameData[0] & 0xFF;
        int pos = 1;

        // MIME type (null-terminated, ISO-8859-1)
        int mimeEnd = findNullTerminator(frameData, pos, 0);
        if (mimeEnd == -1) {
            return "[OBJECT: Invalid MIME type]";
        }

        String mimeType = new String(frameData, pos, mimeEnd - pos, StandardCharsets.ISO_8859_1);
        pos = mimeEnd + 1;

        // Filename (null-terminated)
        int filenameEnd = findNullTerminator(frameData, pos, encoding);
        String filename = "";
        if (filenameEnd != -1) {
            byte[] filenameData = new byte[filenameEnd - pos];
            System.arraycopy(frameData, pos, filenameData, 0, filenameData.length);
            filename = decodeText(filenameData, encoding, majorVersion);
            pos = filenameEnd + getNullTerminatorSize(encoding);
        }

        // Description (null-terminated)
        int descEnd = findNullTerminator(frameData, pos, encoding);
        String description = "";
        if (descEnd != -1) {
            byte[] descData = new byte[descEnd - pos];
            System.arraycopy(frameData, pos, descData, 0, descData.length);
            description = decodeText(descData, encoding, majorVersion);
            pos = descEnd + getNullTerminatorSize(encoding);
        }

        int objectDataSize = frameData.length - pos;

        String result = "[OBJECT:" + mimeType + "," + objectDataSize + " bytes";
        if (!filename.isEmpty()) {
            result += ",file:" + filename;
        }
        if (!description.isEmpty()) {
            result += ",desc:" + description;
        }
        result += "]";

        return result;
    }

    private String extractPrivateFromFrame(byte[] frameData) {
        if (frameData.length == 0) {
            return "";
        }

        // Owner identifier (null-terminated) + Private data
        int ownerEnd = findNullTerminator(frameData, 0, 0); // Owner ist ISO-8859-1
        if (ownerEnd == -1) {
            return "[PRIVATE:" + frameData.length + " bytes]";
        }

        String owner = new String(frameData, 0, ownerEnd, StandardCharsets.ISO_8859_1);
        int dataSize = frameData.length - ownerEnd - 1;

        return "[PRIVATE:" + owner + "," + dataSize + " bytes]";
    }

    private String parseGenre(String genre) {
        if (genre == null || genre.isEmpty()) {
            return genre;
        }

        // ID3v2 Genre kann verschiedene Formate haben:
        // "(13)" -> Numeric reference zu ID3v1 Genre
        // "(13)Rock" -> Numeric + Text
        // "Rock" -> Pure text
        // "(RX)(CR)" -> Refinements
        StringBuilder result = new StringBuilder();
        Pattern pattern = Pattern.compile("\\((\\d+)\\)");
        Matcher matcher = pattern.matcher(genre);

        int lastEnd = 0;
        while (matcher.find()) {
            // Text vor der Nummer hinzufügen
            if (matcher.start() > lastEnd) {
                String textBefore = genre.substring(lastEnd, matcher.start()).trim();
                if (!textBefore.isEmpty() && result.length() == 0) { // Änderung: Nur hinzufügen, wenn result leer
                    result.append(textBefore);
                }
            }

            // Genre-Nummer konvertieren
            try {
                int genreNum = Integer.parseInt(matcher.group(1));
                if (genreNum >= 0 && genreNum < ID3V1_GENRES.length) {
                    if (result.length() == 0) { // Änderung: Nur hinzufügen, wenn result leer
                        result.append(ID3V1_GENRES[genreNum]);
                    }
                }
            } catch (NumberFormatException e) {
                // Ungültige Nummer, ignorieren
            }

            lastEnd = matcher.end();
        }

        // Restlichen Text hinzufügen
        if (lastEnd < genre.length() && result.length() == 0) { // Änderung: Nur hinzufügen, wenn result leer
            String textAfter = genre.substring(lastEnd).trim();
            if (!textAfter.isEmpty()) {
                result.append(textAfter);
            }
        }

        return result.length() > 0 ? result.toString() : genre;
    }

    private String getPictureTypeDescription(int type) {
        switch (type) {
            case 0: return "Other";
            case 1: return "32x32 file icon";
            case 2: return "Other file icon";
            case 3: return "Cover (front)";
            case 4: return "Cover (back)";
            case 5: return "Leaflet page";
            case 6: return "Media";
            case 7: return "Lead artist";
            case 8: return "Artist";
            case 9: return "Conductor";
            case 10: return "Band";
            case 11: return "Composer";
            case 12: return "Lyricist";
            case 13: return "Recording location";
            case 14: return "During recording";
            case 15: return "During performance";
            case 16: return "Movie screen capture";
            case 17: return "Bright colored fish";
            case 18: return "Illustration";
            case 19: return "Band logotype";
            case 20: return "Publisher logotype";
            default: return "Unknown(" + type + ")";
        }
    }

    private int findNullTerminator(byte[] data, int start, int encoding) {
        int nullSize = getNullTerminatorSize(encoding);

        for (int i = start; i <= data.length - nullSize; i++) {
            boolean isNull = true;
            for (int j = 0; j < nullSize; j++) {
                if (data[i + j] != 0) {
                    isNull = false;
                    break;
                }
            }
            if (isNull) {
                return i;
            }
        }
        return -1;
    }

    private int getNullTerminatorSize(int encoding) {
        switch (encoding) {
            case UTF_16:
            case UTF_16BE:
                return 2; // UTF-16 braucht 2 null bytes
            default:
                return 1; // ISO-8859-1 und UTF-8 brauchen 1 null byte
        }
    }

    private String decodeText(byte[] data, int encoding, int majorVersion) {
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
                // UTF-8 ist nur in ID3v2.4 gültig
                if (majorVersion >= 4) {
                    charset = StandardCharsets.UTF_8;
                } else {
                    LOGGER.warning("UTF-8 encoding in ID3v2." + majorVersion + " - treating as ISO-8859-1");
                    charset = StandardCharsets.ISO_8859_1;
                }
                break;
            default:
                LOGGER.warning("Unknown text encoding: " + encoding + " - using ISO-8859-1");
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
            LOGGER.fine("Created fallback handler for unknown ID3 field: " + key);
        }
    }
}