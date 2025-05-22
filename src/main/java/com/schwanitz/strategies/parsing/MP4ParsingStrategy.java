package com.schwanitz.strategies.parsing;

import com.schwanitz.interfaces.FieldHandler;
import com.schwanitz.interfaces.Metadata;
import com.schwanitz.others.MetadataField;
import com.schwanitz.others.TextFieldHandler;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.tagging.TagFormat;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class MP4ParsingStrategy implements TagParsingStrategy {
    private static final Logger LOGGER = Logger.getLogger(MP4ParsingStrategy.class.getName());

    // MP4 Atom Types für Metadaten
    private static final String MOOV_ATOM = "moov";
    private static final String UDTA_ATOM = "udta";
    private static final String META_ATOM = "meta";
    private static final String ILST_ATOM = "ilst";
    private static final String DATA_ATOM = "data";

    // Bekannte MP4 Metadata Atoms (erweitert)
    private static final Map<String, String> KNOWN_ATOMS = new HashMap<>();

    static {
        // iTunes Standard Atoms
        KNOWN_ATOMS.put("©nam", "Title");        // Title
        KNOWN_ATOMS.put("©ART", "Artist");       // Artist
        KNOWN_ATOMS.put("©alb", "Album");        // Album
        KNOWN_ATOMS.put("©day", "Date");         // Date
        KNOWN_ATOMS.put("©gen", "Genre");        // Genre
        KNOWN_ATOMS.put("trkn", "Track");        // Track Number
        KNOWN_ATOMS.put("disk", "Disc");         // Disc Number
        KNOWN_ATOMS.put("©cmt", "Comment");      // Comment
        KNOWN_ATOMS.put("©wrt", "Composer");     // Composer
        KNOWN_ATOMS.put("aART", "AlbumArtist");  // Album Artist
        KNOWN_ATOMS.put("©grp", "Grouping");     // Grouping
        KNOWN_ATOMS.put("cpil", "Compilation");  // Compilation flag
        KNOWN_ATOMS.put("©too", "Encoder");      // Encoder
        KNOWN_ATOMS.put("tmpo", "BPM");          // BPM
        KNOWN_ATOMS.put("©lyr", "Lyrics");       // Lyrics
        KNOWN_ATOMS.put("desc", "Description");  // Description
        KNOWN_ATOMS.put("ldes", "LongDescription"); // Long Description
        KNOWN_ATOMS.put("©xyz", "GPS");          // GPS coordinates
        KNOWN_ATOMS.put("rtng", "Rating");       // Rating
        KNOWN_ATOMS.put("stik", "MediaType");    // Media Type
        KNOWN_ATOMS.put("pcst", "Podcast");      // Podcast flag
        KNOWN_ATOMS.put("catg", "Category");     // Category
        KNOWN_ATOMS.put("keyw", "Keywords");     // Keywords
        KNOWN_ATOMS.put("purd", "PurchaseDate"); // Purchase Date
        KNOWN_ATOMS.put("purl", "PodcastURL");   // Podcast URL
        KNOWN_ATOMS.put("egid", "EpisodeGUID");  // Episode GUID
        KNOWN_ATOMS.put("sosn", "ShowName");     // Show Name
        KNOWN_ATOMS.put("tven", "TVEpisode");    // TV Episode
        KNOWN_ATOMS.put("tvsn", "TVSeason");     // TV Season
        KNOWN_ATOMS.put("tvsh", "TVShow");       // TV Show

        // Copyright und Legal
        KNOWN_ATOMS.put("©cpy", "Copyright");    // Copyright
        KNOWN_ATOMS.put("©phg", "RecordingCopyright"); // Recording Copyright

        // Sort-Felder (sehr wichtig für Bibliothek-Software)
        KNOWN_ATOMS.put("©las", "ArtistSort");   // Artist Sort
        KNOWN_ATOMS.put("©lal", "AlbumArtistSort"); // Album Artist Sort
        KNOWN_ATOMS.put("©lcp", "ComposerSort"); // Composer Sort
        KNOWN_ATOMS.put("©lsn", "AlbumSort");    // Album Sort
        KNOWN_ATOMS.put("©lts", "TitleSort");    // Title Sort

        // Cover Art (kritisch für Media-Player)
        KNOWN_ATOMS.put("covr", "CoverArt");     // Cover Art

        // Erweiterte Metadaten
        KNOWN_ATOMS.put("©aut", "Author");       // Author
        KNOWN_ATOMS.put("©dir", "Director");     // Director
        KNOWN_ATOMS.put("©prd", "Producer");     // Producer
        KNOWN_ATOMS.put("©src", "Source");       // Source
        KNOWN_ATOMS.put("©inf", "Information");  // Information
        KNOWN_ATOMS.put("©req", "Requirements"); // Requirements
        KNOWN_ATOMS.put("©fmt", "Format");       // Format
        KNOWN_ATOMS.put("©ope", "OriginalArtist"); // Original Artist

        // iTunes Store Integration
        KNOWN_ATOMS.put("atID", "iTunes_Album_ID");    // iTunes Album ID
        KNOWN_ATOMS.put("cnID", "iTunes_Catalog_ID");  // iTunes Catalog ID
        KNOWN_ATOMS.put("geID", "iTunes_Genre_ID");    // iTunes Genre ID
        KNOWN_ATOMS.put("plID", "Playlist_ID");        // Playlist ID
        KNOWN_ATOMS.put("sfID", "StoreFront_ID");      // Store Front ID
        KNOWN_ATOMS.put("akID", "iTunes_Account_ID");  // iTunes Account ID
        KNOWN_ATOMS.put("apID", "iTunes_Artist_ID");   // iTunes Artist ID

        // Audio-spezifische erweiterte Felder
        KNOWN_ATOMS.put("©mvc", "Movement_Count");     // Movement Count
        KNOWN_ATOMS.put("©mvi", "Movement_Number");    // Movement Number
        KNOWN_ATOMS.put("©mvn", "Movement_Name");      // Movement Name
        KNOWN_ATOMS.put("shwm", "Show_Movement");      // Show Movement
        KNOWN_ATOMS.put("©wrk", "Work_Name");          // Work Name

        // Technische Metadaten
        KNOWN_ATOMS.put("©enc", "Encoded_By");         // Encoded By
        KNOWN_ATOMS.put("©swr", "Software_Version");   // Software Version
        KNOWN_ATOMS.put("©mak", "Make");               // Make
        KNOWN_ATOMS.put("©mod", "Model");              // Model
        KNOWN_ATOMS.put("©alc", "AlbumComposer");      // Album Composer

        // Zusätzliche Podcast/Audiobook Felder
        KNOWN_ATOMS.put("©nrt", "Narrator");           // Narrator
        KNOWN_ATOMS.put("©pub", "Publisher");          // Publisher
        KNOWN_ATOMS.put("publ", "Publication_Date");   // Publication Date
        KNOWN_ATOMS.put("©st3", "Subtitle");           // Subtitle

        // Bewertung und Kategorisierung
        KNOWN_ATOMS.put("rate", "Rating_User");        // User Rating
        KNOWN_ATOMS.put("©rtu", "Rating_User_Text");   // User Rating Text
        KNOWN_ATOMS.put("©rtd", "Rating_RIAA");        // RIAA Rating
        KNOWN_ATOMS.put("©con", "Conductor");          // Conductor
        KNOWN_ATOMS.put("©sol", "Soloist");            // Soloist
        KNOWN_ATOMS.put("©lnt", "Liner_Notes");        // Liner Notes

        // Genre-Erweiterte Felder
        KNOWN_ATOMS.put("gnre", "GenreID");            // Genre ID (numeric)
        KNOWN_ATOMS.put("©sty", "Style");              // Style
        KNOWN_ATOMS.put("©moo", "Mood");               // Mood

        // Technische Audio-Eigenschaften
        KNOWN_ATOMS.put("©key", "InitialKey");         // Initial Key
        KNOWN_ATOMS.put("©bpm", "BeatsPerMinute");     // BPM (alternative)
        KNOWN_ATOMS.put("©tmp", "Tempo");              // Tempo

        // Label und Vertrieb
        KNOWN_ATOMS.put("©lab", "RecordLabel");        // Record Label
        KNOWN_ATOMS.put("©dis", "Distributor");        // Distributor
        KNOWN_ATOMS.put("©cat", "CatalogNumber");      // Catalog Number
        KNOWN_ATOMS.put("©isn", "ISRC");               // ISRC Code
        KNOWN_ATOMS.put("©upc", "UPC");                // UPC Code

        // Video-spezifische Felder (für MP4 Video)
        KNOWN_ATOMS.put("©des", "LongDescription_Video"); // Long Description for Video
        KNOWN_ATOMS.put("©syn", "Synopsis");           // Synopsis
        KNOWN_ATOMS.put("©snm", "Sort_Name");          // Sort Name
        KNOWN_ATOMS.put("©snk", "Sort_Keyword");       // Sort Keyword

        // Erweiterte technische Felder
        KNOWN_ATOMS.put("©ed1", "EditDate1");          // Edit Date 1
        KNOWN_ATOMS.put("©ed2", "EditDate2");          // Edit Date 2
        KNOWN_ATOMS.put("©ed3", "EditDate3");          // Edit Date 3
        KNOWN_ATOMS.put("©yrr", "Year_Recorded");      // Year Recorded
        KNOWN_ATOMS.put("©yrc", "Year_Composed");      // Year Composed

        // Spezielle iTunes Felder
        KNOWN_ATOMS.put("pcst", "Podcast_Flag");       // Podcast Flag
        KNOWN_ATOMS.put("purd", "Purchase_Date");      // Purchase Date
        KNOWN_ATOMS.put("ownr", "Owner");              // Owner
        KNOWN_ATOMS.put("xid ", "XID");                // XID (Cross-reference ID)
    }

    private final Map<String, FieldHandler<?>> handlers;

    public MP4ParsingStrategy() {
        this.handlers = new HashMap<>();
        initializeDefaultHandlers();
    }

    private void initializeDefaultHandlers() {
        // Handler für alle bekannten MP4 Felder erstellen
        for (String fieldName : KNOWN_ATOMS.values()) {
            handlers.put(fieldName, new TextFieldHandler(fieldName));
        }
    }

    @Override
    public boolean canHandle(TagFormat format) {
        return format == TagFormat.MP4;
    }

    @Override
    public Metadata parseTag(TagFormat format, RandomAccessFile file, long offset, long size) throws IOException {
        MP4Metadata metadata = new MP4Metadata();
        parseMP4Atoms(file, metadata, offset, size);
        return metadata;
    }

    private void parseMP4Atoms(RandomAccessFile file, MP4Metadata metadata, long offset, long size)
            throws IOException {
        // Navigiere zum moov Atom
        long moovOffset = findAtom(file, offset, size, MOOV_ATOM);
        if (moovOffset == -1) {
            throw new IOException("No moov atom found in MP4 file");
        }

        file.seek(moovOffset);
        long moovSize = readBigEndianUInt32(file);

        // Navigiere zu udta -> meta -> ilst
        long udtaOffset = findAtom(file, moovOffset + 8, moovSize - 8, UDTA_ATOM);
        if (udtaOffset == -1) {
            LOGGER.fine("No udta atom found - no metadata available");
            return;
        }

        file.seek(udtaOffset);
        long udtaSize = readBigEndianUInt32(file);

        long metaOffset = findAtom(file, udtaOffset + 8, udtaSize - 8, META_ATOM);
        if (metaOffset == -1) {
            LOGGER.fine("No meta atom found in udta");
            return;
        }

        file.seek(metaOffset);
        long metaSize = readBigEndianUInt32(file);
        file.skipBytes(4); // Skip meta version/flags

        long ilstOffset = findAtom(file, metaOffset + 12, metaSize - 12, ILST_ATOM);
        if (ilstOffset == -1) {
            LOGGER.fine("No ilst atom found in meta");
            return;
        }

        file.seek(ilstOffset);
        long ilstSize = readBigEndianUInt32(file);

        // Parse metadata items in ilst
        parseMetadataItems(file, metadata, ilstOffset + 8, ilstSize - 8);

        LOGGER.fine("Successfully parsed MP4 metadata");
    }

    private void parseMetadataItems(RandomAccessFile file, MP4Metadata metadata, long offset, long size)
            throws IOException {
        long currentPos = offset;
        long endPos = offset + size;

        while (currentPos < endPos - 8) {
            file.seek(currentPos);

            long itemSize = readBigEndianUInt32(file);
            if (itemSize < 8 || itemSize > endPos - currentPos) {
                break;
            }

            byte[] atomTypeBytes = new byte[4];
            file.read(atomTypeBytes);
            String atomType = new String(atomTypeBytes, StandardCharsets.ISO_8859_1);

            // Parse data within this metadata item
            parseMetadataItem(file, metadata, atomType, currentPos + 8, itemSize - 8);

            currentPos += itemSize;
        }
    }

    private void parseMetadataItem(RandomAccessFile file, MP4Metadata metadata, String atomType,
                                   long offset, long size) throws IOException {
        // Suche nach 'data' Atom innerhalb des Metadata Items
        long dataOffset = findAtom(file, offset, size, DATA_ATOM);
        if (dataOffset == -1) {
            LOGGER.fine("No data atom found for " + atomType);
            return;
        }

        file.seek(dataOffset);
        long dataSize = readBigEndianUInt32(file);
        file.skipBytes(4); // Skip 'data'

        if (dataSize < 16) { // 8 bytes header + 8 bytes minimal data header
            LOGGER.fine("Data atom too small for " + atomType);
            return;
        }

        // Data type and locale
        int dataType = (int) readBigEndianUInt32(file);
        int locale = (int) readBigEndianUInt32(file);

        long valueSize = dataSize - 16; // Subtract atom header (8) + data header (8)
        if (valueSize <= 0 || valueSize > 65536) { // Sanity check
            LOGGER.fine("Invalid value size for " + atomType + ": " + valueSize);
            return;
        }

        // Read value data
        byte[] valueData = new byte[(int) valueSize];
        file.read(valueData);

        // Parse value based on data type
        String value = parseDataValue(valueData, dataType, atomType);
        if (value != null && !value.isEmpty()) {
            String fieldName = KNOWN_ATOMS.getOrDefault(atomType, atomType);
            addField(metadata, fieldName, value);

            LOGGER.fine("Parsed MP4 field: " + atomType + " (" + fieldName + ") = " +
                    (value.length() > 50 ? value.substring(0, 50) + "..." : value));
        }
    }

    private String parseDataValue(byte[] data, int dataType, String atomType) {
        switch (dataType) {
            case 1: // UTF-8 text
                return new String(data, StandardCharsets.UTF_8).trim();

            case 2: // UTF-16 text (mit BOM)
                return new String(data, StandardCharsets.UTF_16).trim();

            case 3: // UTF-16BE text (ohne BOM)
                return new String(data, StandardCharsets.UTF_16BE).trim();

            case 13: // JPEG image
                if ("covr".equals(atomType)) {
                    // Für Cover Art: Base64-Encoding für bessere Handhabung
                    String base64 = Base64.getEncoder().encodeToString(data);
                    return "[JPEG:" + data.length + " bytes,base64:" +
                            (base64.length() > 100 ? base64.substring(0, 100) + "..." : base64) + "]";
                }
                return "[JPEG:" + data.length + " bytes]";

            case 14: // PNG image
                if ("covr".equals(atomType)) {
                    // Für Cover Art: Base64-Encoding für bessere Handhabung
                    String base64 = Base64.getEncoder().encodeToString(data);
                    return "[PNG:" + data.length + " bytes,base64:" +
                            (base64.length() > 100 ? base64.substring(0, 100) + "..." : base64) + "]";
                }
                return "[PNG:" + data.length + " bytes]";

            case 15: // GIF image
                if ("covr".equals(atomType)) {
                    String base64 = Base64.getEncoder().encodeToString(data);
                    return "[GIF:" + data.length + " bytes,base64:" +
                            (base64.length() > 100 ? base64.substring(0, 100) + "..." : base64) + "]";
                }
                return "[GIF:" + data.length + " bytes]";

            case 21: // Signed integer (big-endian)
                if (data.length == 1) {
                    return String.valueOf(data[0]);
                } else if (data.length == 2) {
                    return String.valueOf(((data[0] & 0xFF) << 8) | (data[1] & 0xFF));
                } else if (data.length == 4) {
                    return String.valueOf(((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16) |
                            ((data[2] & 0xFF) << 8) | (data[3] & 0xFF));
                } else if (data.length == 8) {
                    // 64-bit integer
                    long value = 0;
                    for (int i = 0; i < 8; i++) {
                        value = (value << 8) | (data[i] & 0xFF);
                    }
                    return String.valueOf(value);
                }
                break;

            case 22: // Unsigned integer (big-endian)
                if (data.length == 1) {
                    return String.valueOf(data[0] & 0xFF);
                } else if (data.length == 2) {
                    return String.valueOf(((data[0] & 0xFF) << 8) | (data[1] & 0xFF));
                } else if (data.length == 4) {
                    long value = ((long)(data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16) |
                            ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
                    return String.valueOf(value);
                } else if (data.length == 8) {
                    // 64-bit unsigned integer
                    long value = 0;
                    for (int i = 0; i < 8; i++) {
                        value = (value << 8) | (data[i] & 0xFF);
                    }
                    return String.valueOf(value);
                }
                break;

            case 23: // 32-bit Float (big-endian)
                if (data.length == 4) {
                    int intBits = ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16) |
                            ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
                    float floatValue = Float.intBitsToFloat(intBits);
                    return String.valueOf(floatValue);
                }
                break;

            case 24: // 64-bit Double (big-endian)
                if (data.length == 8) {
                    long longBits = 0;
                    for (int i = 0; i < 8; i++) {
                        longBits = (longBits << 8) | (data[i] & 0xFF);
                    }
                    double doubleValue = Double.longBitsToDouble(longBits);
                    return String.valueOf(doubleValue);
                }
                break;

            case 0: // Binary data
            default:
                // Handle special cases
                if ("trkn".equals(atomType) || "disk".equals(atomType)) {
                    // Track/Disc number format: 0 0 current total
                    if (data.length >= 6) {
                        int current = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
                        int total = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
                        if (total > 0) {
                            return current + "/" + total;
                        } else {
                            return String.valueOf(current);
                        }
                    } else if (data.length >= 4) {
                        // Alternative format: current total
                        int current = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                        int total = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
                        if (total > 0) {
                            return current + "/" + total;
                        } else {
                            return String.valueOf(current);
                        }
                    }
                }

                // Boolean flags (single byte)
                if (("cpil".equals(atomType) || "pcst".equals(atomType) || "shwm".equals(atomType))
                        && data.length == 1) {
                    return data[0] != 0 ? "true" : "false";
                }

                // Genre ID (numeric genre)
                if ("gnre".equals(atomType) && data.length == 2) {
                    int genreId = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                    return String.valueOf(genreId);
                }

                // Try as UTF-8 text as fallback
                try {
                    String text = new String(data, StandardCharsets.UTF_8).trim();
                    // Check if it's valid UTF-8 text
                    if (!text.isEmpty() && isValidText(text)) {
                        return text;
                    }
                } catch (Exception e) {
                    // Not valid UTF-8
                }

                // Last resort: binary data representation
                return "[BINARY:" + data.length + " bytes]";
        }

        return "[UNKNOWN_TYPE:" + dataType + "]";
    }

    private boolean isValidText(String text) {
        // Simple heuristic: check if text contains mostly printable characters
        int printableCount = 0;
        for (char c : text.toCharArray()) {
            if (c >= 32 && c <= 126) { // ASCII printable range
                printableCount++;
            } else if (c >= 128) { // Unicode characters
                printableCount++;
            } else if (c == 9 || c == 10 || c == 13) { // Tab, LF, CR
                printableCount++;
            }
        }
        return printableCount > text.length() * 0.8; // At least 80% printable
    }

    private long findAtom(RandomAccessFile file, long offset, long size, String atomType)
            throws IOException {
        long currentPos = offset;
        long endPos = offset + size;

        while (currentPos < endPos - 8) {
            file.seek(currentPos);

            long atomSize = readBigEndianUInt32(file);
            if (atomSize < 8 || atomSize > endPos - currentPos) {
                break;
            }

            byte[] typeBytes = new byte[4];
            file.read(typeBytes);
            String type = new String(typeBytes, StandardCharsets.ISO_8859_1);

            if (atomType.equals(type)) {
                return currentPos;
            }

            currentPos += atomSize;
        }

        return -1; // Not found
    }

    private long readBigEndianUInt32(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[4];
        file.read(bytes);
        return ((long)(bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16) |
                ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
    }

    @SuppressWarnings("unchecked")
    private void addField(MP4Metadata metadata, String key, String value) {
        FieldHandler<?> handler = handlers.get(key);
        if (handler != null) {
            metadata.addField(new MetadataField<>(key, value, (FieldHandler<String>) handler));
        } else {
            // Fallback: TextFieldHandler für unbekannte Felder
            TextFieldHandler textHandler = new TextFieldHandler(key);
            metadata.addField(new MetadataField<>(key, value, textHandler));
            LOGGER.fine("Created fallback handler for unknown MP4 field: " + key);
        }
    }

    public void registerHandler(String key, FieldHandler<?> handler) {
        handlers.put(key, handler);
    }

    // Innere Klasse für MP4 Metadata
    public static class MP4Metadata implements Metadata {
        private final List<MetadataField<?>> fields = new ArrayList<>();

        @Override
        public String getTagFormat() {
            return TagFormat.MP4.getFormatName();
        }

        @Override
        public List<MetadataField<?>> getFields() {
            return fields;
        }

        @Override
        public void addField(MetadataField<?> field) {
            fields.add(field);
        }
    }
}