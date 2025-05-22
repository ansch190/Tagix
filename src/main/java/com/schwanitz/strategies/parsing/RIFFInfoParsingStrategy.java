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

public class RIFFInfoParsingStrategy implements TagParsingStrategy {
    private static final Logger LOGGER = Logger.getLogger(RIFFInfoParsingStrategy.class.getName());

    // RIFF INFO Chunk IDs (4 Zeichen)
    private static final Map<String, String> INFO_CHUNKS = new HashMap<>();

    static {
        // Audio-relevante Kern-Metadaten
        INFO_CHUNKS.put("INAM", "Title");            // Name/Title
        INFO_CHUNKS.put("IART", "Artist");           // Artist
        INFO_CHUNKS.put("IPRD", "Album");            // Product (Album)
        INFO_CHUNKS.put("ICRD", "Date");             // Creation Date
        INFO_CHUNKS.put("IGNR", "Genre");            // Genre
        INFO_CHUNKS.put("ICMT", "Comment");          // Comment
        INFO_CHUNKS.put("ICOP", "Copyright");        // Copyright

        // Produktions-Metadaten
        INFO_CHUNKS.put("IENG", "Engineer");         // Engineer
        INFO_CHUNKS.put("ITCH", "Technician");       // Technician
        INFO_CHUNKS.put("ISFT", "Software");         // Software
        INFO_CHUNKS.put("IPRO", "Producer");         // Producer
        INFO_CHUNKS.put("ICMS", "Commissioned");     // Commissioned

        // Quelle/Medium
        INFO_CHUNKS.put("IMED", "Medium");           // Medium
        INFO_CHUNKS.put("ISRC", "Source");           // Source
        INFO_CHUNKS.put("ISRF", "SourceForm");       // Source Form
        INFO_CHUNKS.put("IARL", "Archival");         // Archival Location

        // Inhalt/Beschreibung
        INFO_CHUNKS.put("ISBJ", "Subject");          // Subject
        INFO_CHUNKS.put("IKEY", "Keywords");         // Keywords
        INFO_CHUNKS.put("ISTR", "Starring");         // Starring
        INFO_CHUNKS.put("IPRT", "Part");             // Part

        // Technische Daten
        INFO_CHUNKS.put("ITRK", "TrackNumber");      // Track Number
        INFO_CHUNKS.put("IFRM", "TotalFrames");      // Total Frames
        INFO_CHUNKS.put("IMUS", "Musician");         // Musician

        // Erweiterte Chunks
        INFO_CHUNKS.put("ISMP", "SMPTETimeCode");    // SMPTE Time Code
        INFO_CHUNKS.put("IDIT", "DigitizationDate"); // Digitization Date
        INFO_CHUNKS.put("IWRI", "WrittenBy");        // Written By

        // Veraltete Chunks (für Kompatibilität beibehalten)
        INFO_CHUNKS.put("IDOS", "DOSName");          // DOS Name (veraltet)
    }

    private final Map<String, FieldHandler<?>> handlers;

    public RIFFInfoParsingStrategy() {
        this.handlers = new HashMap<>();
        initializeDefaultHandlers();
    }

    private void initializeDefaultHandlers() {
        // Handler für alle bekannten RIFF INFO Felder erstellen
        for (String fieldName : INFO_CHUNKS.values()) {
            handlers.put(fieldName, new TextFieldHandler(fieldName));
        }
    }

    @Override
    public boolean canHandle(TagFormat format) {
        return format == TagFormat.RIFF_INFO;
    }

    @Override
    public Metadata parseTag(TagFormat format, RandomAccessFile file, long offset, long size) throws IOException {
        RIFFInfoMetadata metadata = new RIFFInfoMetadata();
        parseRIFFInfoChunk(file, metadata, offset, size);
        return metadata;
    }

    private void parseRIFFInfoChunk(RandomAccessFile file, RIFFInfoMetadata metadata, long offset, long size)
            throws IOException {
        file.seek(offset);

        // LIST Chunk Header lesen
        byte[] listHeader = new byte[8];
        file.read(listHeader);

        String chunkId = new String(listHeader, 0, 4, StandardCharsets.US_ASCII);
        if (!"LIST".equals(chunkId)) {
            throw new IOException("Expected LIST chunk, found: " + chunkId);
        }

        int chunkSize = readLittleEndianInt32(listHeader, 4);
        if (chunkSize < 4) {
            throw new IOException("Invalid LIST chunk size: " + chunkSize);
        }

        // INFO Identifier lesen
        byte[] infoId = new byte[4];
        file.read(infoId);
        String infoType = new String(infoId, StandardCharsets.US_ASCII);

        if (!"INFO".equals(infoType)) {
            throw new IOException("Expected INFO list type, found: " + infoType);
        }

        LOGGER.fine("Parsing RIFF INFO chunk with size: " + chunkSize);

        // INFO Sub-Chunks parsen
        long currentPos = offset + 12; // 8 bytes LIST header + 4 bytes INFO type
        long endPos = offset + 8 + chunkSize; // 8 bytes LIST header + chunk size

        int fieldCount = 0;
        while (currentPos < endPos - 8) { // Mindestens 8 Bytes für Sub-Chunk Header
            file.seek(currentPos);

            byte[] subChunkHeader = new byte[8];
            int bytesRead = file.read(subChunkHeader);
            if (bytesRead != 8) {
                break;
            }

            String subChunkId = new String(subChunkHeader, 0, 4, StandardCharsets.US_ASCII);
            int subChunkSize = readLittleEndianInt32(subChunkHeader, 4);

            if (subChunkSize < 0 || subChunkSize > endPos - currentPos - 8) {
                LOGGER.warning("Invalid sub-chunk size for " + subChunkId + ": " + subChunkSize);
                break;
            }

            // Sub-Chunk Daten lesen
            if (subChunkSize > 0) {
                byte[] subChunkData = new byte[subChunkSize];
                file.read(subChunkData);

                // Null-terminated String parsen
                String value = parseNullTerminatedString(subChunkData);

                if (!value.isEmpty()) {
                    String fieldName = INFO_CHUNKS.getOrDefault(subChunkId, subChunkId);
                    addField(metadata, fieldName, value);
                    fieldCount++;

                    LOGGER.fine("Parsed RIFF INFO field: " + subChunkId + " (" + fieldName + ") = " +
                            (value.length() > 50 ? value.substring(0, 50) + "..." : value));
                }
            }

            // Nächste Position berechnen (mit Padding für gerade Byte-Grenze)
            currentPos += 8 + subChunkSize;
            if (subChunkSize % 2 != 0) {
                currentPos++; // Padding Byte
            }
        }

        LOGGER.fine("Successfully parsed RIFF INFO chunk with " + fieldCount + " fields");
    }

    private String parseNullTerminatedString(byte[] data) {
        // Finde das erste Null-Byte
        int length = data.length;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == 0) {
                length = i;
                break;
            }
        }

        if (length == 0) {
            return "";
        }

        // Versuche verschiedene Encodings
        try {
            // Versuche UTF-8
            String utf8 = new String(data, 0, length, StandardCharsets.UTF_8);
            if (isValidString(utf8)) {
                return utf8.trim();
            }
        } catch (Exception e) {
            // Fallback zu ISO-8859-1
        }

        try {
            // Fallback zu ISO-8859-1 (Windows-1252 ähnlich)
            return new String(data, 0, length, StandardCharsets.ISO_8859_1).trim();
        } catch (Exception e) {
            // Als letzter Ausweg: US-ASCII
            return new String(data, 0, length, StandardCharsets.US_ASCII).trim();
        }
    }

    private boolean isValidString(String str) {
        // Einfache Heuristik: String sollte druckbare Zeichen enthalten
        for (char c : str.toCharArray()) {
            if (c < 32 && c != 9 && c != 10 && c != 13) { // Kontrollzeichen außer Tab, LF, CR
                return false;
            }
        }
        return true;
    }

    private int readLittleEndianInt32(byte[] data, int offset) {
        return ((data[offset] & 0xFF)) |
                ((data[offset + 1] & 0xFF) << 8) |
                ((data[offset + 2] & 0xFF) << 16) |
                ((data[offset + 3] & 0xFF) << 24);
    }

    @SuppressWarnings("unchecked")
    private void addField(RIFFInfoMetadata metadata, String key, String value) {
        FieldHandler<?> handler = handlers.get(key);
        if (handler != null) {
            metadata.addField(new MetadataField<>(key, value, (FieldHandler<String>) handler));
        } else {
            // Fallback: TextFieldHandler für unbekannte Felder
            TextFieldHandler textHandler = new TextFieldHandler(key);
            metadata.addField(new MetadataField<>(key, value, textHandler));
            LOGGER.fine("Created fallback handler for unknown RIFF INFO field: " + key);
        }
    }

    public void registerHandler(String key, FieldHandler<?> handler) {
        handlers.put(key, handler);
    }

    // Innere Klasse für RIFF INFO Metadata
    public static class RIFFInfoMetadata implements Metadata {
        private final List<MetadataField<?>> fields = new ArrayList<>();

        @Override
        public String getTagFormat() {
            return TagFormat.RIFF_INFO.getFormatName();
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