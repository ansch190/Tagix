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
import java.util.logging.Level;
import java.util.logging.Logger;

public class APEParsingStrategy implements TagParsingStrategy {
    private static final Logger LOGGER = Logger.getLogger(APEParsingStrategy.class.getName());

    // APE Item Flags
    private static final int APE_ITEM_TYPE_MASK = 0x06;
    private static final int APE_ITEM_TYPE_UTF8 = 0x00;
    private static final int APE_ITEM_TYPE_BINARY = 0x02;
    private static final int APE_ITEM_TYPE_EXTERNAL = 0x04;
    private static final int APE_ITEM_TYPE_RESERVED = 0x06;

    private static final int APE_ITEM_READ_ONLY = 0x01;

    // Performance-Optimierung
    private static final int READ_BUFFER_SIZE = 8192;

    private final Map<String, FieldHandler<?>> handlers;
    private final Map<String, String> keyNormalizations;

    public APEParsingStrategy() {
        this.handlers = new HashMap<>();
        this.keyNormalizations = new HashMap<>();
        initializeDefaultHandlers();
        initializeKeyNormalizations();
    }

    private void initializeDefaultHandlers() {
        // Standard APE Felder (case-sensitive)
        handlers.put("Title", new TextFieldHandler("Title"));
        handlers.put("Artist", new TextFieldHandler("Artist"));
        handlers.put("Album", new TextFieldHandler("Album"));
        handlers.put("Year", new TextFieldHandler("Year"));
        handlers.put("Track", new TextFieldHandler("Track"));
        handlers.put("Genre", new TextFieldHandler("Genre"));
        handlers.put("Comment", new TextFieldHandler("Comment"));
        handlers.put("AlbumArtist", new TextFieldHandler("AlbumArtist"));
        handlers.put("Composer", new TextFieldHandler("Composer"));
        handlers.put("Conductor", new TextFieldHandler("Conductor"));
        handlers.put("Disc", new TextFieldHandler("Disc"));
        handlers.put("ISRC", new TextFieldHandler("ISRC"));
        handlers.put("Label", new TextFieldHandler("Label"));
        handlers.put("Catalog", new TextFieldHandler("Catalog"));
        handlers.put("Barcode", new TextFieldHandler("Barcode"));
        handlers.put("Language", new TextFieldHandler("Language"));
        handlers.put("Bibliography", new TextFieldHandler("Bibliography"));
        handlers.put("Media", new TextFieldHandler("Media"));
        handlers.put("Index", new TextFieldHandler("Index"));
        handlers.put("Related", new TextFieldHandler("Related"));
        handlers.put("Abstract", new TextFieldHandler("Abstract"));
        handlers.put("Copyright", new TextFieldHandler("Copyright"));
        handlers.put("Publicationright", new TextFieldHandler("Publicationright"));
        handlers.put("File", new TextFieldHandler("File"));

        // Erweiterte Metadaten
        handlers.put("Subtitle", new TextFieldHandler("Subtitle"));
        handlers.put("Publisher", new TextFieldHandler("Publisher"));
        handlers.put("CatalogNumber", new TextFieldHandler("CatalogNumber"));
        handlers.put("EAN/UPN", new TextFieldHandler("EAN/UPN"));
        handlers.put("ISBN", new TextFieldHandler("ISBN"));
        handlers.put("LC", new TextFieldHandler("LC"));
        handlers.put("RecordLocation", new TextFieldHandler("RecordLocation"));
        handlers.put("RecordDate", new TextFieldHandler("RecordDate"));
        handlers.put("PurchaseDate", new TextFieldHandler("PurchaseDate"));
        handlers.put("PurchaseOwner", new TextFieldHandler("PurchaseOwner"));
        handlers.put("PurchasePrice", new TextFieldHandler("PurchasePrice"));
        handlers.put("PurchaseCurrency", new TextFieldHandler("PurchaseCurrency"));

        // MusicBrainz IDs (wichtig für Musikdatenbanken)
        handlers.put("MUSICBRAINZ_TRACKID", new TextFieldHandler("MUSICBRAINZ_TRACKID"));
        handlers.put("MUSICBRAINZ_ALBUMID", new TextFieldHandler("MUSICBRAINZ_ALBUMID"));
        handlers.put("MUSICBRAINZ_ARTISTID", new TextFieldHandler("MUSICBRAINZ_ARTISTID"));
        handlers.put("MUSICBRAINZ_ALBUMARTISTID", new TextFieldHandler("MUSICBRAINZ_ALBUMARTISTID"));
        handlers.put("MUSICBRAINZ_RELEASEGROUPID", new TextFieldHandler("MUSICBRAINZ_RELEASEGROUPID"));

        // Sortierfelder
        handlers.put("ALBUMSORT", new TextFieldHandler("ALBUMSORT"));
        handlers.put("ALBUMARTISTSORT", new TextFieldHandler("ALBUMARTISTSORT"));
        handlers.put("ARTISTSORT", new TextFieldHandler("ARTISTSORT"));
        handlers.put("TITLESORT", new TextFieldHandler("TITLESORT"));

        // Technische Audio-Eigenschaften
        handlers.put("BPM", new TextFieldHandler("BPM"));
        handlers.put("InitialKey", new TextFieldHandler("InitialKey"));
        handlers.put("Mood", new TextFieldHandler("Mood"));
        handlers.put("Occasion", new TextFieldHandler("Occasion"));
        handlers.put("Quality", new TextFieldHandler("Quality"));
        handlers.put("Tempo", new TextFieldHandler("Tempo"));

        // ReplayGain Felder
        handlers.put("REPLAYGAIN_TRACK_GAIN", new TextFieldHandler("REPLAYGAIN_TRACK_GAIN"));
        handlers.put("REPLAYGAIN_TRACK_PEAK", new TextFieldHandler("REPLAYGAIN_TRACK_PEAK"));
        handlers.put("REPLAYGAIN_ALBUM_GAIN", new TextFieldHandler("REPLAYGAIN_ALBUM_GAIN"));
        handlers.put("REPLAYGAIN_ALBUM_PEAK", new TextFieldHandler("REPLAYGAIN_ALBUM_PEAK"));

        // Technische Felder
        handlers.put("Tool", new TextFieldHandler("Tool"));
        handlers.put("Encoder", new TextFieldHandler("Encoder"));
    }

    private void initializeKeyNormalizations() {
        // Bekannte Varianten normalisieren
        keyNormalizations.put("album artist", "AlbumArtist");
        keyNormalizations.put("albumartist", "AlbumArtist");
        keyNormalizations.put("ALBUMARTIST", "AlbumArtist");
        keyNormalizations.put("replaygain_track_gain", "REPLAYGAIN_TRACK_GAIN");
        keyNormalizations.put("replaygain_album_gain", "REPLAYGAIN_ALBUM_GAIN");
        keyNormalizations.put("replaygain_track_peak", "REPLAYGAIN_TRACK_PEAK");
        keyNormalizations.put("replaygain_album_peak", "REPLAYGAIN_ALBUM_PEAK");
        keyNormalizations.put("musicbrainz_trackid", "MUSICBRAINZ_TRACKID");
        keyNormalizations.put("musicbrainz_albumid", "MUSICBRAINZ_ALBUMID");
        keyNormalizations.put("musicbrainz_artistid", "MUSICBRAINZ_ARTISTID");
        keyNormalizations.put("musicbrainz_albumartistid", "MUSICBRAINZ_ALBUMARTISTID");
        keyNormalizations.put("musicbrainz_releasegroupid", "MUSICBRAINZ_RELEASEGROUPID");
    }

    @Override
    public boolean canHandle(TagFormat format) {
        return format == TagFormat.APEV1 || format == TagFormat.APEV2;
    }

    @Override
    public Metadata parseTag(TagFormat format, RandomAccessFile file, long offset, long size) throws IOException {
        APEMetadata metadata = new APEMetadata(format);
        parseAPETag(file, metadata, offset, size, format);
        return metadata;
    }

    private void parseAPETag(RandomAccessFile file, APEMetadata metadata, long offset, long size, TagFormat format)
            throws IOException {
        file.seek(offset);

        // APE Header lesen (32 Bytes)
        byte[] header = new byte[32];
        int bytesRead = file.read(header);

        if (bytesRead != 32) {
            throw new IOException("Could not read complete APE header");
        }

        // Preamble prüfen ("APETAGEX")
        String preamble = new String(header, 0, 8, StandardCharsets.US_ASCII);
        if (!"APETAGEX".equals(preamble)) {
            throw new IOException("Invalid APE tag preamble: " + preamble);
        }

        // Version prüfen (bytes 8-11, little-endian)
        int version = readLittleEndianInt32(header, 8);
        if (version != 1000 && version != 2000) {
            throw new IOException("Unsupported APE version: " + version);
        }

        // Tag Size (bytes 12-15) - Größe ohne Header, inklusive Footer
        int tagSize = readLittleEndianInt32(header, 12);

        // Item Count (bytes 16-19, little-endian)
        int itemCount = readLittleEndianInt32(header, 16);

        // Tag Flags (bytes 20-23, little-endian)
        int tagFlags = readLittleEndianInt32(header, 20);

        boolean hasHeader = (tagFlags & 0x80000000) != 0;
        boolean hasFooter = (tagFlags & 0x40000000) != 0;
        boolean isHeader = (tagFlags & 0x20000000) != 0;
        boolean isReadOnly = (tagFlags & 0x00000001) != 0;

        // Erweiterte Logging-Informationen
        LOGGER.fine(String.format(
                "APE Tag Details: Version=%d, Size=%d bytes, Items=%d, " +
                        "Flags=0x%08X (Header:%b, Footer:%b, IsHeader:%b, ReadOnly:%b)",
                version, tagSize, itemCount, tagFlags,
                hasHeader, hasFooter, isHeader, isReadOnly
        ));

        // Sanity checks
        if (itemCount < 0 || itemCount > 1000) {
            throw new IOException("Invalid APE item count: " + itemCount);
        }

        if (tagSize < 0 || tagSize > size) {
            throw new IOException("Invalid APE tag size: " + tagSize);
        }

        // Item Position Berechnung
        long itemsStart;
        long itemsEnd;

        if (isHeader) {
            // Wir sind am Header - Items folgen direkt
            itemsStart = offset + 32;
            if (hasFooter) {
                itemsEnd = offset + tagSize - 32;  // Vor dem Footer
            } else {
                itemsEnd = offset + tagSize;
            }
        } else {
            // Wir sind am Footer
            if (hasHeader) {
                // Header + Items + Footer Konfiguration
                itemsStart = offset - tagSize + 32;
                itemsEnd = offset;
            } else {
                // Nur Items + Footer Konfiguration
                itemsStart = offset - tagSize;
                itemsEnd = offset;
            }
        }

        LOGGER.fine("Items range: " + itemsStart + " to " + itemsEnd + " (size: " + (itemsEnd - itemsStart) + ")");

        // Items parsen
        long currentPos = itemsStart;
        for (int i = 0; i < itemCount; i++) {
            try {
                if (currentPos >= itemsEnd - 8) {
                    LOGGER.warning("Not enough space for item " + (i + 1));
                    break;
                }

                currentPos = parseAPEItem(file, metadata, currentPos, version, itemsEnd);

                if (currentPos > itemsEnd) {
                    LOGGER.warning("APE item parsing exceeded tag boundary");
                    break;
                }

                // Padding überspringen
                if (currentPos < itemsEnd) {
                    file.seek(currentPos);
                    while (currentPos < itemsEnd && file.readByte() == 0) {
                        currentPos++;
                    }
                    if (currentPos < itemsEnd) {
                        file.seek(currentPos);
                    }
                }

            } catch (IOException e) {
                LOGGER.warning("Error parsing APE item " + (i + 1) + ": " + e.getMessage());
                break;
            }
        }

        LOGGER.fine("Successfully parsed APE tag with " + itemCount + " items");
    }

    private long parseAPEItem(RandomAccessFile file, APEMetadata metadata, long position, int version, long maxPos)
            throws IOException {
        file.seek(position);

        // Item Header lesen (8 Bytes)
        byte[] itemHeader = new byte[8];
        file.read(itemHeader);

        // Item Value Size (4 bytes, little-endian)
        int valueSize = readLittleEndianInt32(itemHeader, 0);

        // Item Flags (4 bytes, little-endian)
        int itemFlags = readLittleEndianInt32(itemHeader, 4);

        if (valueSize < 0 || valueSize > 1048576) { // 1MB limit
            throw new IOException("Invalid APE item value size: " + valueSize);
        }

        long currentPos = position + 8;

        // Key-Lesung mit korrekter UTF-8 Behandlung
        List<Byte> keyBytes = new ArrayList<>();
        while (currentPos < maxPos) {
            byte b = file.readByte();
            currentPos++;

            if (b == 0) {
                break; // Null-Terminator gefunden
            }

            keyBytes.add(b);

            if (keyBytes.size() > 255) { // Sanity check für Key-Länge
                throw new IOException("APE item key too long");
            }
        }

        if (keyBytes.isEmpty()) {
            throw new IOException("Empty APE item key");
        }

        // Direkte UTF-8 Dekodierung der gesammelten Bytes
        byte[] keyByteArray = new byte[keyBytes.size()];
        for (int i = 0; i < keyBytes.size(); i++) {
            keyByteArray[i] = keyBytes.get(i);
        }
        String key = new String(keyByteArray, StandardCharsets.UTF_8);

        // Key-Validierung
        if (!isValidAPEKey(key)) {
            LOGGER.warning("Invalid APE key detected: " + key);
            return currentPos + valueSize;
        }

        // Key normalisieren für bessere Kompatibilität
        String normalizedKey = normalizeAPEKey(key);

        // Value lesen
        if (currentPos + valueSize > maxPos) {
            throw new IOException("APE item value extends beyond tag boundary");
        }

        byte[] valueData = readBytes(file, valueSize);
        currentPos += valueSize;

        // Item Type bestimmen
        int itemType = itemFlags & APE_ITEM_TYPE_MASK;
        boolean isReadOnly = (itemFlags & APE_ITEM_READ_ONLY) != 0;

        // Erweiterte Logging
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest(String.format(
                    "APE Item: Key='%s' (normalized: '%s'), Type=%d, Size=%d, ReadOnly=%b",
                    key, normalizedKey, itemType, valueSize, isReadOnly
            ));
        }

        String value;
        switch (itemType) {
            case APE_ITEM_TYPE_UTF8:
                // Multi-Value Support für UTF-8 Text
                value = parseMultiValueField(valueData);
                break;

            case APE_ITEM_TYPE_BINARY:
                // Binäre Daten - verbesserte Behandlung
                value = processBinaryItem(normalizedKey, valueData);
                LOGGER.fine("Binary APE item: " + normalizedKey + " (" + valueSize + " bytes)");
                break;

            case APE_ITEM_TYPE_EXTERNAL:
                // Externe Referenz
                value = "[EXTERNAL:" + new String(valueData, StandardCharsets.UTF_8).trim() + "]";
                break;

            default:
                LOGGER.warning("Unknown APE item type: " + itemType + " for key: " + normalizedKey);
                value = "[UNKNOWN:" + valueSize + " bytes]";
                break;
        }

        // Feld hinzufügen (nur wenn nicht leer)
        if (!value.isEmpty()) {
            addField(metadata, normalizedKey, value);
            LOGGER.fine("Parsed APE item: " + normalizedKey + " = " +
                    (value.length() > 50 ? value.substring(0, 50) + "..." : value) +
                    (isReadOnly ? " [read-only]" : ""));
        }

        return currentPos;
    }

    private String parseMultiValueField(byte[] valueData) {
        List<String> values = new ArrayList<>();
        int start = 0;

        for (int i = 0; i < valueData.length; i++) {
            if (valueData[i] == 0) {
                if (i > start) {
                    String value = new String(valueData, start, i - start, StandardCharsets.UTF_8).trim();
                    if (!value.isEmpty()) {
                        values.add(value);
                    }
                }
                start = i + 1;
            }
        }

        // Letzter Wert (falls kein abschließender Null-Terminator)
        if (start < valueData.length) {
            String value = new String(valueData, start, valueData.length - start, StandardCharsets.UTF_8).trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }

        return values.isEmpty() ? "" : String.join("; ", values);
    }

    private String processBinaryItem(String key, byte[] valueData) {
        // Spezielle Behandlung für bekannte Binary-Felder
        if ("Cover Art (Front)".equalsIgnoreCase(key) ||
                "Cover Art (Back)".equalsIgnoreCase(key) ||
                key.toLowerCase().contains("cover")) {

            // Bildformat erkennen
            String format = detectImageFormat(valueData);
            String preview = "";

            if (valueData.length > 0) {
                // Base64 Preview für kleine Bilder
                int previewSize = Math.min(valueData.length, 100);
                byte[] previewData = new byte[previewSize];
                System.arraycopy(valueData, 0, previewData, 0, previewSize);
                preview = Base64.getEncoder().encodeToString(previewData);
            }

            return String.format("[IMAGE:%s,%d bytes,preview:%s%s]",
                    format, valueData.length, preview,
                    valueData.length > 100 ? "..." : "");
        }

        return "[BINARY:" + valueData.length + " bytes]";
    }

    private String detectImageFormat(byte[] data) {
        if (data.length < 4) return "unknown";

        // JPEG
        if (data[0] == (byte)0xFF && data[1] == (byte)0xD8) {
            return "JPEG";
        }
        // PNG
        if (data[0] == (byte)0x89 && data[1] == 'P' &&
                data[2] == 'N' && data[3] == 'G') {
            return "PNG";
        }
        // GIF
        if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F') {
            return "GIF";
        }
        // BMP
        if (data[0] == 'B' && data[1] == 'M') {
            return "BMP";
        }

        return "unknown";
    }

    private boolean isValidAPEKey(String key) {
        if (key == null || key.isEmpty() || key.length() > 255) {
            return false;
        }

        // APE Keys: ASCII 0x20-0x7E, außer bestimmte Zeichen
        for (char c : key.toCharArray()) {
            if (c < 0x20 || c > 0x7E) {
                return false;
            }
            // Verbotene Zeichen in APE Keys
            if (c == '[' || c == ']' || c == '=' || c == '\0') {
                return false;
            }
        }

        // Reservierte Keys prüfen
        if (key.equalsIgnoreCase("ID3") || key.equalsIgnoreCase("TAG") ||
                key.equalsIgnoreCase("OggS") || key.equalsIgnoreCase("MP+")) {
            LOGGER.warning("Reserved APE key detected: " + key);
            return false;
        }

        return true;
    }

    private String normalizeAPEKey(String key) {
        String lowerKey = key.toLowerCase();
        return keyNormalizations.getOrDefault(lowerKey, key);
    }

    private byte[] readBytes(RandomAccessFile file, int size) throws IOException {
        if (size <= 0) return new byte[0];

        byte[] data = new byte[size];
        int totalRead = 0;

        while (totalRead < size) {
            int toRead = Math.min(READ_BUFFER_SIZE, size - totalRead);
            int read = file.read(data, totalRead, toRead);
            if (read < 0) {
                throw new IOException("Unexpected end of file");
            }
            totalRead += read;
        }

        return data;
    }

    private int readLittleEndianInt32(byte[] data, int offset) {
        return ((data[offset] & 0xFF)) |
                ((data[offset + 1] & 0xFF) << 8) |
                ((data[offset + 2] & 0xFF) << 16) |
                ((data[offset + 3] & 0xFF) << 24);
    }

    @SuppressWarnings("unchecked")
    private void addField(APEMetadata metadata, String key, String value) {
        FieldHandler<?> handler = handlers.get(key);
        if (handler != null) {
            metadata.addField(new MetadataField<>(key, value, (FieldHandler<String>) handler));
        } else {
            // Fallback: TextFieldHandler für unbekannte Felder erstellen
            TextFieldHandler textHandler = new TextFieldHandler(key);
            metadata.addField(new MetadataField<>(key, value, textHandler));
            LOGGER.fine("Created fallback handler for unknown APE field: " + key);
        }
    }

    public void registerHandler(String key, FieldHandler<?> handler) {
        handlers.put(key, handler);
    }

    // Innere Klasse für APE Metadata
    public static class APEMetadata implements Metadata {
        private final List<MetadataField<?>> fields = new ArrayList<>();
        private final TagFormat format;

        public APEMetadata(TagFormat format) {
            this.format = format;
        }

        @Override
        public String getTagFormat() {
            return format.getFormatName();
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