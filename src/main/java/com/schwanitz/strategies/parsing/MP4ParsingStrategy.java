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

    // Bekannte MP4 Metadata Atoms
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

            case 13: // JPEG image
            case 14: // PNG image
                return "[IMAGE:" + data.length + " bytes]";

            case 21: // Signed integer (big-endian)
                if (data.length == 1) {
                    return String.valueOf(data[0]);
                } else if (data.length == 2) {
                    return String.valueOf(((data[0] & 0xFF) << 8) | (data[1] & 0xFF));
                } else if (data.length == 4) {
                    return String.valueOf(((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16) |
                            ((data[2] & 0xFF) << 8) | (data[3] & 0xFF));
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
                    }
                }

                // Try as UTF-8 text as fallback
                try {
                    return new String(data, StandardCharsets.UTF_8).trim();
                } catch (Exception e) {
                    return "[BINARY:" + data.length + " bytes]";
                }
        }

        return "[UNKNOWN_TYPE:" + dataType + "]";
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