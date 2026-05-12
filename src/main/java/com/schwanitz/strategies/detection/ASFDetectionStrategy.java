package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Erkennungsstrategie für ASF-Dateien (Advanced Systems Format).
 * <p>
 * ASF wird für .wma-, .asf- und .wmv-Dateien mit Windows-Media-Metadaten verwendet.
 * Struktur:
 * <ul>
 *   <li>ASF Header Object GUID: 75B22630-668E-11CF-A6D9-00AA0062CE6C</li>
 *   <li>Header Object Größe (8 Bytes Little-Endian)</li>
 *   <li>Anzahl der Header-Objekte (4 Bytes)</li>
 *   <li>Reserviert (2 Bytes)</li>
 * </ul>
 * <p>
 * Wichtige Metadaten-Objekte:
 * <ul>
 *   <li>Content Description: Grundlegende Metadaten (Titel, Autor usw.)</li>
 *   <li>Extended Content Description: Erweiterte Metadatenfelder</li>
 *   <li>Metadata Object: Zusätzliche Metadaten</li>
 *   <li>Metadata Library Object: Große Metadatensammlungen</li>
 * </ul>
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

    // ASF structural sizes
    private static final int GUID_SIZE = 16;
    private static final int ASF_HEADER_OBJECT_SIZE = 30; // 16 (GUID) + 8 (size) + 4 (count) + 2 (reserved)
    private static final int ASF_OBJECT_MIN_SIZE = 24;    // 16 (GUID) + 8 (size)
    private static final int ASF_HEADER_EXT_DATA_OFFSET = 46; // Header ext: GUID(16) + size(8) + reserved1(16) + reserved2(2) + data_size(4)
    private static final int MAX_HEADER_OBJECTS = 10000;

    /**
     * {@inheritDoc}
     * <p>
     * Gibt die unterstützten ASF-Formate zurück: ASF_CONTENT_DESC, ASF_EXT_CONTENT_DESC.
     */
    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.ASF_CONTENT_DESC, TagFormat.ASF_EXT_CONTENT_DESC);
    }

    /**
     * Prüft, ob die Dateidaten ein ASF-Format enthalten, anhand der
     * ASF-Header-Object-GUID am Dateianfang.
     *
     * @param startBuffer Puffer mit den ersten Bytes der Datei (mindestens 16 Bytes)
     * @param endBuffer   Puffer mit den letzten Bytes der Datei (nicht verwendet)
     * @return {@code true}, wenn die ASF-Header-GUID erkannt wurde
     */
    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (startBuffer.length < GUID_SIZE) {
            return false;
        }
        return Arrays.equals(Arrays.copyOfRange(startBuffer, 0, GUID_SIZE), ASF_HEADER_GUID);
    }

    /**
     * Analysiert die ASF-Header-Objekte und ermittelt alle Metadaten-Objekte
     * (Content Description, Extended Content Description, Metadata, Metadata Library).
     * <p>
     * Durchläuft alle Header-Objekte und die Header Extension, um ASF-Metadaten zu finden.
     *
     * @param file        die geöffnete Datei
     * @param filePath    der Dateipfad zur Protokollierung
     * @param startBuffer Puffer mit den ersten Bytes der Datei
     * @param endBuffer   Puffer mit den letzten Bytes der Datei
     * @return eine Liste der erkannten {@link TagInfo}-Objekte
     * @throws IOException wenn ein Fehler beim Lesen der Datei auftritt
     */
    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath,
                                    byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        if (!canDetect(startBuffer, endBuffer)) {
            return tags;
        }

        LOG.debug("Detecting ASF tags in file: {}", filePath);

        try {
            ASFHeader asfHeader = parseASFHeader(file);
            if (asfHeader == null) {
                LOG.debug("Failed to parse ASF header");
                return tags;
            }

            LOG.debug("ASF Header: size={}, objects={}", asfHeader.headerSize, asfHeader.numHeaderObjects);

            long currentPos = ASF_HEADER_OBJECT_SIZE;
            long headerEnd = asfHeader.headerSize;

            for (int i = 0; i < asfHeader.numHeaderObjects && currentPos + ASF_OBJECT_MIN_SIZE < headerEnd; i++) {
                ASFObject object = parseASFObject(file, currentPos);

                if (object == null) {
                    LOG.warn("Failed to parse ASF object {} at position {}", i + 1, currentPos);
                    break;
                }

                TagInfo tagInfo = processASFObject(object, currentPos);
                if (tagInfo != null) {
                    tags.add(tagInfo);
                }

                if (Arrays.equals(object.guid, HEADER_EXT_GUID)) {
                    List<TagInfo> extTags = processHeaderExtensionObject(file, currentPos, object.size);
                    tags.addAll(extTags);
                }

                currentPos += object.size;
            }

            LOG.debug("ASF detection completed: found {} metadata objects", tags.size());

        } catch (IOException e) {
            LOG.error("Error detecting ASF tags in {}: {}", filePath, e.getMessage());
            throw e;
        }

        return tags;
    }

    private ASFHeader parseASFHeader(RandomAccessFile file) throws IOException {
        file.seek(0);

        byte[] headerGuid = new byte[GUID_SIZE];
        file.read(headerGuid);

        if (!Arrays.equals(headerGuid, ASF_HEADER_GUID)) {
            LOG.debug("Invalid ASF header GUID");
            return null;
        }

        long headerSize = readLittleEndianLong(file);
        int numHeaderObjects = readLittleEndianInt(file);
        int reserved = readLittleEndianShort(file);

        if (headerSize < ASF_HEADER_OBJECT_SIZE || headerSize > file.length()) {
            LOG.warn("Invalid ASF header size: {}", headerSize);
            return null;
        }

        if (numHeaderObjects < 0 || numHeaderObjects > MAX_HEADER_OBJECTS) {
            LOG.warn("Invalid ASF header object count: {}", numHeaderObjects);
            return null;
        }

        return new ASFHeader(headerSize, numHeaderObjects, reserved);
    }

    private ASFObject parseASFObject(RandomAccessFile file, long offset) throws IOException {
        file.seek(offset);

        byte[] objectGuid = new byte[GUID_SIZE];
        file.read(objectGuid);

        long objectSize = readLittleEndianLong(file);

        if (objectSize < ASF_OBJECT_MIN_SIZE) {
            LOG.warn("Invalid ASF object size: {} at offset: {}", objectSize, offset);
            return null;
        }

        return new ASFObject(objectGuid, objectSize);
    }

    private TagInfo processASFObject(ASFObject object, long offset) {
        if (Arrays.equals(object.guid, CONTENT_DESC_GUID)) {
            LOG.debug("Found ASF Content Description Object at offset: {}, size: {} bytes", offset, object.size);
            return new TagInfo(TagFormat.ASF_CONTENT_DESC, offset, object.size);
        }

        if (Arrays.equals(object.guid, EXT_CONTENT_DESC_GUID)) {
            LOG.debug("Found ASF Extended Content Description Object at offset: {}, size: {} bytes", offset, object.size);
            return new TagInfo(TagFormat.ASF_EXT_CONTENT_DESC, offset, object.size);
        }

        if (Arrays.equals(object.guid, METADATA_GUID) || Arrays.equals(object.guid, METADATA_LIBRARY_GUID)) {
            LOG.debug("Found ASF Metadata Object at offset: {}, size: {} bytes", offset, object.size);
            return new TagInfo(TagFormat.ASF_EXT_CONTENT_DESC, offset, object.size);
        }

        return null;
    }

    private List<TagInfo> processHeaderExtensionObject(RandomAccessFile file, long headerExtOffset,
                                                       long headerExtSize) throws IOException {
        List<TagInfo> extTags = new ArrayList<>();

        try {
            file.seek(headerExtOffset + ASF_OBJECT_MIN_SIZE);

            byte[] reserved1 = new byte[GUID_SIZE];
            file.read(reserved1);

            int reserved2 = readLittleEndianShort(file);
            int extDataSize = readLittleEndianInt(file);

            if (extDataSize <= 0 || extDataSize > headerExtSize - ASF_HEADER_EXT_DATA_OFFSET) {
                LOG.debug("Invalid Header Extension data size: {}", extDataSize);
                return extTags;
            }

            long extDataStart = headerExtOffset + ASF_HEADER_EXT_DATA_OFFSET;
            long extDataEnd = extDataStart + extDataSize;
            long currentPos = extDataStart;

            while (currentPos + ASF_OBJECT_MIN_SIZE < extDataEnd) {
                ASFObject extObject = parseASFObject(file, currentPos);

                if (extObject == null || extObject.size > extDataEnd - currentPos) {
                    break;
                }

                TagInfo extTagInfo = processASFObject(extObject, currentPos);
                if (extTagInfo != null) {
                    extTags.add(extTagInfo);
                }

                currentPos += extObject.size;
            }

        } catch (IOException e) {
            LOG.debug("Error parsing ASF Header Extension: {}", e.getMessage());
        }

        return extTags;
    }

    private int readLittleEndianShort(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[2];
        file.read(bytes);
        return ((bytes[0] & 0xFF)) | ((bytes[1] & 0xFF) << 8);
    }

    private int readLittleEndianInt(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[4];
        file.read(bytes);
        return ((bytes[0] & 0xFF)) |
                ((bytes[1] & 0xFF) << 8) |
                ((bytes[2] & 0xFF) << 16) |
                ((bytes[3] & 0xFF) << 24);
    }

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

    private static class ASFObject {
        final byte[] guid;
        final long size;

        ASFObject(byte[] guid, long size) {
            this.guid = guid;
            this.size = size;
        }
    }
}