package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Detection Strategy für ASF (Advanced Systems Format) Dateien
 * Unterstützt: .wma, .asf, .wmv Dateien mit Windows Media Metadaten
 *
 * ASF Header Struktur:
 * - ASF Header Object GUID (16 bytes): 75B22630-668E-11CF-A6D9-00AA0062CE6C
 * - Header Object Size (8 bytes, little-endian): Größe des gesamten Headers
 * - Number of Header Objects (4 bytes, little-endian): Anzahl der Sub-Objects
 * - Reserved (2 bytes): Immer 0x01, 0x02
 *
 * Wichtige Object GUIDs:
 * - Header Object: 75B22630-668E-11CF-A6D9-00AA0062CE6C
 * - Content Description Object: 75B22633-668E-11CF-A6D9-00AA0062CE6C
 * - Extended Content Description Object: D2D0A440-E307-11D2-97F0-00A0C95EA850
 * - Header Extension Object: 5FBF03B5-A92E-11CF-8EE3-00C00C205365
 * - Metadata Object: C5F8CBEA-5BAF-4877-8467-AA8C44FA4CCA
 * - Metadata Library Object: 44231C94-9498-49D1-A141-1D134E457054
 *
 * ASF verwendet GUID-basierte Identifikation für alle Objects.
 * Alle Integers sind Little-Endian.
 * Unicode-Strings sind UTF-16LE kodiert.
 */
public class ASFDetectionStrategy extends TagDetectionStrategy {

    // ASF Header Object GUID: 75B22630-668E-11CF-A6D9-00AA0062CE6C
    private static final byte[] ASF_HEADER_GUID = {
            0x30, 0x26, (byte)0xB2, 0x75, (byte)0x8E, 0x66, (byte)0xCF, 0x11,
            (byte)0xA6, (byte)0xD9, 0x00, (byte)0xAA, 0x00, 0x62, (byte)0xCE, 0x6C
    };

    // Content Description Object GUID: 75B22633-668E-11CF-A6D9-00AA0062CE6C
    private static final byte[] CONTENT_DESC_GUID = {
            0x33, 0x26, (byte)0xB2, 0x75, (byte)0x8E, 0x66, (byte)0xCF, 0x11,
            (byte)0xA6, (byte)0xD9, 0x00, (byte)0xAA, 0x00, 0x62, (byte)0xCE, 0x6C
    };

    // Extended Content Description Object GUID: D2D0A440-E307-11D2-97F0-00A0C95EA850
    private static final byte[] EXT_CONTENT_DESC_GUID = {
            0x40, (byte)0xA4, (byte)0xD0, (byte)0xD2, 0x07, (byte)0xE3, (byte)0xD2, 0x11,
            (byte)0x97, (byte)0xF0, 0x00, (byte)0xA0, (byte)0xC9, 0x5E, (byte)0xA8, 0x50
    };

    // Header Extension Object GUID: 5FBF03B5-A92E-11CF-8EE3-00C00C205365
    private static final byte[] HEADER_EXT_GUID = {
            (byte)0xB5, 0x03, (byte)0xBF, 0x5F, 0x2E, (byte)0xA9, (byte)0xCF, 0x11,
            (byte)0x8E, (byte)0xE3, 0x00, (byte)0xC0, 0x0C, 0x20, 0x53, 0x65
    };

    // Metadata Object GUID: C5F8CBEA-5BAF-4877-8467-AA8C44FA4CCA
    private static final byte[] METADATA_GUID = {
            (byte)0xEA, (byte)0xCB, (byte)0xF8, (byte)0xC5, (byte)0xAF, 0x5B, 0x77, 0x48,
            (byte)0x84, 0x67, (byte)0xAA, (byte)0x8C, 0x44, (byte)0xFA, 0x4C, (byte)0xCA
    };

    // Metadata Library Object GUID: 44231C94-9498-49D1-A141-1D134E457054
    private static final byte[] METADATA_LIBRARY_GUID = {
            (byte)0x94, 0x1C, 0x23, 0x44, (byte)0x98, (byte)0x94, (byte)0xD1, 0x49,
            (byte)0xA1, 0x41, 0x1D, 0x13, 0x4E, 0x45, 0x70, 0x54
    };

    // Stream Properties Object GUID: B7DC0791-A9B7-11CF-8EE6-00C00C205365
    private static final byte[] STREAM_PROPERTIES_GUID = {
            (byte)0x91, 0x07, (byte)0xDC, (byte)0xB7, (byte)0xB7, (byte)0xA9, (byte)0xCF, 0x11,
            (byte)0x8E, (byte)0xE6, 0x00, (byte)0xC0, 0x0C, 0x20, 0x53, 0x65
    };

    // File Properties Object GUID: 8CABDCA1-A947-11CF-8EE4-00C00C205365
    private static final byte[] FILE_PROPERTIES_GUID = {
            (byte)0xA1, (byte)0xDC, (byte)0xAB, (byte)0x8C, 0x47, (byte)0xA9, (byte)0xCF, 0x11,
            (byte)0x8E, (byte)0xE4, 0x00, (byte)0xC0, 0x0C, 0x20, 0x53, 0x65
    };

    // Digital Signature Object GUID: 2211B3FC-BD23-11D2-B4B7-00A0C955FC6E
    private static final byte[] DIGITAL_SIGNATURE_GUID = {
            (byte)0xFC, (byte)0xB3, 0x11, 0x22, 0x23, (byte)0xBD, (byte)0xD2, 0x11,
            (byte)0xB4, (byte)0xB7, 0x00, (byte)0xA0, (byte)0xC9, 0x55, (byte)0xFC, 0x6E
    };

    // GUID zu Name Mapping für besseres Logging
    private static final Map<String, String> GUID_NAMES = new HashMap<>();

    static {
        GUID_NAMES.put(bytesToHex(ASF_HEADER_GUID), "ASF Header");
        GUID_NAMES.put(bytesToHex(CONTENT_DESC_GUID), "Content Description");
        GUID_NAMES.put(bytesToHex(EXT_CONTENT_DESC_GUID), "Extended Content Description");
        GUID_NAMES.put(bytesToHex(HEADER_EXT_GUID), "Header Extension");
        GUID_NAMES.put(bytesToHex(METADATA_GUID), "Metadata");
        GUID_NAMES.put(bytesToHex(METADATA_LIBRARY_GUID), "Metadata Library");
        GUID_NAMES.put(bytesToHex(STREAM_PROPERTIES_GUID), "Stream Properties");
        GUID_NAMES.put(bytesToHex(FILE_PROPERTIES_GUID), "File Properties");
        GUID_NAMES.put(bytesToHex(DIGITAL_SIGNATURE_GUID), "Digital Signature");
    }

    @Override
    public List<TagFormat> getSupportedFormats() {
        return List.of(TagFormat.ASF_CONTENT_DESC, TagFormat.ASF_EXT_CONTENT_DESC);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (startBuffer.length < 16) {
            return false;
        }

        // Prüfe ASF Header GUID am Dateianfang
        return Arrays.equals(Arrays.copyOfRange(startBuffer, 0, 16), ASF_HEADER_GUID);
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath,
                                    byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        if (!canDetect(startBuffer, endBuffer)) {
            return tags;
        }

        Log.debug("Detecting ASF tags in file: {}", filePath);

        try {
            // ASF Header lesen und validieren
            ASFHeader asfHeader = parseASFHeader(file);
            if (asfHeader == null) {
                Log.debug("Failed to parse ASF header");
                return tags;
            }

            Log.debug("ASF Header: size={}, objects={}, version={}",
                    asfHeader.headerSize, asfHeader.numHeaderObjects, asfHeader.reserved);

            // Header Objects durchsuchen
            long currentPos = 30; // Nach ASF Header (16 + 8 + 4 + 2)
            long headerEnd = asfHeader.headerSize;

            for (int i = 0; i < asfHeader.numHeaderObjects && currentPos + 24 < headerEnd; i++) {
                ASFObject object = parseASFObject(file, currentPos);

                if (object == null) {
                    Log.warn("Failed to parse ASF object {} at position {}", i + 1, currentPos);
                    break;
                }

                // Metadaten-Objects identifizieren und TagInfo erstellen
                TagInfo tagInfo = processASFObject(object, currentPos);
                if (tagInfo != null) {
                    tags.add(tagInfo);
                }

                // Header Extension Objects können weitere Metadaten enthalten
                if (Arrays.equals(object.guid, HEADER_EXT_GUID)) {
                    List<TagInfo> extTags = processHeaderExtensionObject(file, currentPos, object.size);
                    tags.addAll(extTags);
                }

                currentPos += object.size;
            }

            Log.debug("ASF detection completed: found {} metadata objects", tags.size());

        } catch (IOException e) {
            Log.error("Error detecting ASF tags in {}: {}", filePath, e.getMessage());
            throw e;
        }

        return tags;
    }

    /**
     * Parst den ASF Header
     */
    private ASFHeader parseASFHeader(RandomAccessFile file) throws IOException {
        file.seek(0);

        // ASF Header GUID (16 bytes)
        byte[] headerGuid = new byte[16];
        file.read(headerGuid);

        if (!Arrays.equals(headerGuid, ASF_HEADER_GUID)) {
            Log.debug("Invalid ASF header GUID");
            return null;
        }

        // Header Object Size (8 bytes, little-endian)
        long headerSize = readLittleEndianLong(file);

        // Number of Header Objects (4 bytes, little-endian)
        int numHeaderObjects = readLittleEndianInt(file);

        // Reserved (2 bytes) - sollte 0x01, 0x02 sein
        int reserved = readLittleEndianShort(file);

        // Sanity checks
        if (headerSize < 30 || headerSize > file.length()) {
            Log.warn("Invalid ASF header size: {}", headerSize);
            return null;
        }

        if (numHeaderObjects < 0 || numHeaderObjects > 10000) {
            Log.warn("Invalid ASF header object count: {}", numHeaderObjects);
            return null;
        }

        if (reserved != 0x0201) { // 0x01, 0x02 in little-endian
            Log.debug("Unusual ASF reserved field: 0x{}", Integer.toHexString(reserved));
        }

        return new ASFHeader(headerSize, numHeaderObjects, reserved);
    }

    /**
     * Parst ein ASF Object
     */
    private ASFObject parseASFObject(RandomAccessFile file, long offset) throws IOException {
        file.seek(offset);

        // Object GUID (16 bytes)
        byte[] objectGuid = new byte[16];
        file.read(objectGuid);

        // Object Size (8 bytes, little-endian)
        long objectSize = readLittleEndianLong(file);

        if (objectSize < 24) { // Minimum: 16 bytes GUID + 8 bytes size
            Log.warn("Invalid ASF object size: {} at offset: {}", objectSize, offset);
            return null;
        }

        return new ASFObject(objectGuid, objectSize);
    }

    /**
     * Verarbeitet ein ASF Object und erstellt TagInfo falls es Metadaten enthält
     */
    private TagInfo processASFObject(ASFObject object, long offset) {
        String guidHex = bytesToHex(object.guid);
        String objectName = GUID_NAMES.getOrDefault(guidHex, "Unknown");

        // Content Description Object
        if (Arrays.equals(object.guid, CONTENT_DESC_GUID)) {
            Log.debug("Found ASF Content Description Object at offset: {}, size: {} bytes",
                    offset, object.size);
            return new TagInfo(TagFormat.ASF_CONTENT_DESC, offset, object.size);
        }

        // Extended Content Description Object
        if (Arrays.equals(object.guid, EXT_CONTENT_DESC_GUID)) {
            Log.debug("Found ASF Extended Content Description Object at offset: {}, size: {} bytes",
                    offset, object.size);
            return new TagInfo(TagFormat.ASF_EXT_CONTENT_DESC, offset, object.size);
        }

        // Metadata Object
        if (Arrays.equals(object.guid, METADATA_GUID)) {
            Log.debug("Found ASF Metadata Object at offset: {}, size: {} bytes",
                    offset, object.size);
            return new TagInfo(TagFormat.ASF_EXT_CONTENT_DESC, offset, object.size);
        }

        // Metadata Library Object
        if (Arrays.equals(object.guid, METADATA_LIBRARY_GUID)) {
            Log.debug("Found ASF Metadata Library Object at offset: {}, size: {} bytes",
                    offset, object.size);
            return new TagInfo(TagFormat.ASF_EXT_CONTENT_DESC, offset, object.size);
        }

        // Andere Objects loggen für Debugging
        if (Log.isTraceEnabled()) {
            Log.trace("Found ASF Object: {} at offset: {}, size: {} bytes",
                    objectName, offset, object.size);
        }

        return null;
    }

    /**
     * Verarbeitet Header Extension Objects, die weitere Metadaten enthalten können
     */
    private List<TagInfo> processHeaderExtensionObject(RandomAccessFile file, long headerExtOffset,
                                                       long headerExtSize) throws IOException {
        List<TagInfo> extTags = new ArrayList<>();

        try {
            file.seek(headerExtOffset + 24); // Skip GUID + Size

            // Header Extension Reserved Field 1 (16 bytes GUID)
            byte[] reserved1 = new byte[16];
            file.read(reserved1);

            // Header Extension Reserved Field 2 (2 bytes)
            int reserved2 = readLittleEndianShort(file);

            // Header Extension Data Size (4 bytes)
            int extDataSize = readLittleEndianInt(file);

            if (extDataSize <= 0 || extDataSize > headerExtSize - 46) {
                Log.debug("Invalid Header Extension data size: {}", extDataSize);
                return extTags;
            }

            Log.debug("Processing Header Extension: reserved2=0x{}, dataSize={}",
                    Integer.toHexString(reserved2), extDataSize);

            long extDataStart = headerExtOffset + 46;
            long extDataEnd = extDataStart + extDataSize;
            long currentPos = extDataStart;

            // Extension Objects durchsuchen
            while (currentPos + 24 < extDataEnd) {
                ASFObject extObject = parseASFObject(file, currentPos);

                if (extObject == null || extObject.size > extDataEnd - currentPos) {
                    break;
                }

                // Prüfe auf weitere Metadaten-Objects in der Extension
                TagInfo extTagInfo = processASFObject(extObject, currentPos);
                if (extTagInfo != null) {
                    extTags.add(extTagInfo);
                    Log.debug("Found metadata in Header Extension: {} at offset: {}",
                            GUID_NAMES.getOrDefault(bytesToHex(extObject.guid), "Unknown"),
                            currentPos);
                }

                currentPos += extObject.size;
            }

        } catch (IOException e) {
            Log.debug("Error parsing ASF Header Extension: {}", e.getMessage());
        }

        return extTags;
    }

    /**
     * Liest einen 16-bit Little-Endian Short
     */
    private int readLittleEndianShort(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[2];
        file.read(bytes);
        return ((bytes[0] & 0xFF)) | ((bytes[1] & 0xFF) << 8);
    }

    /**
     * Liest einen 32-bit Little-Endian Integer
     */
    private int readLittleEndianInt(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[4];
        file.read(bytes);
        return ((bytes[0] & 0xFF)) |
                ((bytes[1] & 0xFF) << 8) |
                ((bytes[2] & 0xFF) << 16) |
                ((bytes[3] & 0xFF) << 24);
    }

    /**
     * Liest einen 64-bit Little-Endian Long
     */
    private long readLittleEndianLong(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[8];
        file.read(bytes);
        return ((long)(bytes[0] & 0xFF)) |
                ((long)(bytes[1] & 0xFF) << 8) |
                ((long)(bytes[2] & 0xFF) << 16) |
                ((long)(bytes[3] & 0xFF) << 24) |
                ((long)(bytes[4] & 0xFF) << 32) |
                ((long)(bytes[5] & 0xFF) << 40) |
                ((long)(bytes[6] & 0xFF) << 48) |
                ((long)(bytes[7] & 0xFF) << 56);
    }

    /**
     * Konvertiert byte array zu Hex-String
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    /**
     * ASF Header Datenklasse
     */
    private static class ASFHeader {
        final long headerSize;
        final int numHeaderObjects;
        final int reserved;

        ASFHeader(long headerSize, int numHeaderObjects, int reserved) {
            this.headerSize = headerSize;
            this.numHeaderObjects = numHeaderObjects;
            this.reserved = reserved;
        }
    }

    /**
     * ASF Object Datenklasse
     */
    private static class ASFObject {
        final byte[] guid;
        final long size;

        ASFObject(byte[] guid, long size) {
            this.guid = guid;
            this.size = size;
        }
    }

    /**
     * Utility-Methoden für ASF GUID Handling
     */
    public static class ASFUtils {

        /**
         * Konvertiert GUID zu lesbarem String Format
         */
        public static String guidToString(byte[] guid) {
            if (guid.length != 16) {
                return "Invalid GUID";
            }

            return String.format("%02X%02X%02X%02X-%02X%02X-%02X%02X-%02X%02X-%02X%02X%02X%02X%02X%02X",
                    guid[3] & 0xFF, guid[2] & 0xFF, guid[1] & 0xFF, guid[0] & 0xFF,
                    guid[5] & 0xFF, guid[4] & 0xFF,
                    guid[7] & 0xFF, guid[6] & 0xFF,
                    guid[8] & 0xFF, guid[9] & 0xFF,
                    guid[10] & 0xFF, guid[11] & 0xFF, guid[12] & 0xFF,
                    guid[13] & 0xFF, guid[14] & 0xFF, guid[15] & 0xFF);
        }

        /**
         * Prüft ob GUID ein Metadaten-Object repräsentiert
         */
        public static boolean isMetadataGUID(byte[] guid) {
            return Arrays.equals(guid, CONTENT_DESC_GUID) ||
                    Arrays.equals(guid, EXT_CONTENT_DESC_GUID) ||
                    Arrays.equals(guid, METADATA_GUID) ||
                    Arrays.equals(guid, METADATA_LIBRARY_GUID);
        }

        /**
         * Gibt den Namen eines bekannten GUIDs zurück
         */
        public static String getGUIDName(byte[] guid) {
            String guidHex = bytesToHex(guid);
            return GUID_NAMES.getOrDefault(guidHex, "Unknown GUID");
        }

        /**
         * Gibt alle bekannten ASF GUIDs zurück
         */
        public static Map<String, String> getKnownGUIDs() {
            return new HashMap<>(GUID_NAMES);
        }
    }
}