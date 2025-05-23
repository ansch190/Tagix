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

public class APEParsingStrategy implements TagParsingStrategy {
    private static final Logger LOGGER = Logger.getLogger(APEParsingStrategy.class.getName());

    // APE Item Flags
    private static final int APE_ITEM_TYPE_MASK = 0x06;
    private static final int APE_ITEM_TYPE_UTF8 = 0x00;
    private static final int APE_ITEM_TYPE_BINARY = 0x02;
    private static final int APE_ITEM_TYPE_EXTERNAL = 0x04;
    private static final int APE_ITEM_TYPE_RESERVED = 0x06;

    private static final int APE_ITEM_READ_ONLY = 0x01;

    private final Map<String, FieldHandler<?>> handlers;

    public APEParsingStrategy() {
        this.handlers = new HashMap<>();
        initializeDefaultHandlers();
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

        // ReplayGain Felder
        handlers.put("REPLAYGAIN_TRACK_GAIN", new TextFieldHandler("REPLAYGAIN_TRACK_GAIN"));
        handlers.put("REPLAYGAIN_TRACK_PEAK", new TextFieldHandler("REPLAYGAIN_TRACK_PEAK"));
        handlers.put("REPLAYGAIN_ALBUM_GAIN", new TextFieldHandler("REPLAYGAIN_ALBUM_GAIN"));
        handlers.put("REPLAYGAIN_ALBUM_PEAK", new TextFieldHandler("REPLAYGAIN_ALBUM_PEAK"));

        // Technische Felder
        handlers.put("Tool", new TextFieldHandler("Tool"));
        handlers.put("Encoder", new TextFieldHandler("Encoder"));
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

        // KORRIGIERT: Tag Size (bytes 12-15) - Größe ohne Header, inklusive Footer
        int tagSize = readLittleEndianInt32(header, 12);

        // Item Count (bytes 16-19, little-endian)
        int itemCount = readLittleEndianInt32(header, 16);

        // KORRIGIERT: Tag Flags (bytes 20-23, little-endian)
        int tagFlags = readLittleEndianInt32(header, 20);

        boolean hasHeader = (tagFlags & 0x80000000) != 0;
        boolean hasFooter = (tagFlags & 0x40000000) != 0;  // KORRIGIERT: != 0 statt == 0
        boolean isHeader = (tagFlags & 0x20000000) != 0;

        LOGGER.fine("APE Tag: version=" + version + ", size=" + tagSize + ", items=" + itemCount +
                ", hasHeader=" + hasHeader + ", hasFooter=" + hasFooter + ", isHeader=" + isHeader);

        // Sanity checks
        if (itemCount < 0 || itemCount > 1000) {
            throw new IOException("Invalid APE item count: " + itemCount);
        }

        if (tagSize < 0 || tagSize > size) {
            throw new IOException("Invalid APE tag size: " + tagSize);
        }

        // KORRIGIERTE Item Position Berechnung
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

        // KORRIGIERT: Key-Lesung mit korrekter UTF-8 Behandlung
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

        // KORRIGIERT: Direkte UTF-8 Dekodierung der gesammelten Bytes
        byte[] keyByteArray = new byte[keyBytes.size()];
        for (int i = 0; i < keyBytes.size(); i++) {
            keyByteArray[i] = keyBytes.get(i);
        }
        String key = new String(keyByteArray, StandardCharsets.UTF_8);

        // Value lesen
        if (currentPos + valueSize > maxPos) {
            throw new IOException("APE item value extends beyond tag boundary");
        }

        byte[] valueData = new byte[valueSize];
        if (valueSize > 0) {
            int bytesRead = file.read(valueData);
            if (bytesRead != valueSize) {
                throw new IOException("Could not read complete APE item value");
            }
        }
        currentPos += valueSize;

        // Item Type bestimmen
        int itemType = itemFlags & APE_ITEM_TYPE_MASK;
        boolean isReadOnly = (itemFlags & APE_ITEM_READ_ONLY) != 0;

        String value;
        switch (itemType) {
            case APE_ITEM_TYPE_UTF8:
                value = new String(valueData, StandardCharsets.UTF_8).trim();
                // Null-Terminatoren entfernen
                value = value.replace("\0", "");
                break;

            case APE_ITEM_TYPE_BINARY:
                // Binäre Daten - als Beschreibung darstellen
                value = "[BINARY:" + valueSize + " bytes]";
                LOGGER.fine("Binary APE item: " + key + " (" + valueSize + " bytes)");
                break;

            case APE_ITEM_TYPE_EXTERNAL:
                // Externe Referenz
                value = "[EXTERNAL:" + new String(valueData, StandardCharsets.UTF_8).trim() + "]";
                break;

            default:
                LOGGER.warning("Unknown APE item type: " + itemType + " for key: " + key);
                value = "[UNKNOWN:" + valueSize + " bytes]";
                break;
        }

        // Feld hinzufügen (nur UTF-8 Textfelder werden als normale Metadaten behandelt)
        if (itemType == APE_ITEM_TYPE_UTF8 && !value.isEmpty()) {
            addField(metadata, key, value);
            LOGGER.fine("Parsed APE item: " + key + " = " +
                    (value.length() > 50 ? value.substring(0, 50) + "..." : value) +
                    (isReadOnly ? " [read-only]" : ""));
        } else if (itemType != APE_ITEM_TYPE_UTF8 && !value.isEmpty()) {
            // Nicht-Text Items trotzdem hinzufügen für Vollständigkeit
            addField(metadata, key, value);
            LOGGER.fine("Parsed non-text APE item: " + key + " = " + value);
        }

        return currentPos;
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