package com.schwanitz.strategies.detection;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
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

    private static final byte[] ASF_HEADER_GUID = {
            0x30, 0x26, (byte)0xB2, 0x75, (byte)0x8E, 0x66, (byte)0xCF, 0x11,
            (byte)0xA6, (byte)0xD9, 0x00, (byte)0xAA, 0x00, 0x62, (byte)0xCE, 0x6C
    };

    private static final byte[] CONTENT_DESC_GUID = {
            0x33, 0x26, (byte)0xB2, 0x75, (byte)0x8E, 0x66, (byte)0xCF, 0x11,
            (byte)0xA6, (byte)0xD9, 0x00, (byte)0xAA, 0x00, 0x62, (byte)0xCE, 0x6C
    };

    private static final byte[] EXT_CONTENT_DESC_GUID = {
            0x40, (byte)0xA4, (byte)0xD0, (byte)0xD2, 0x07, (byte)0xE3, (byte)0xD2, 0x11,
            (byte)0x97, (byte)0xF0, 0x00, (byte)0xA0, (byte)0xC9, 0x5E, (byte)0xA8, 0x50
    };

    private static final byte[] HEADER_EXT_GUID = {
            (byte)0xB5, 0x03, (byte)0xBF, 0x5F, 0x2E, (byte)0xA9, (byte)0xCF, 0x11,
            (byte)0x8E, (byte)0xE3, 0x00, (byte)0xC0, 0x0C, 0x20, 0x53, 0x65
    };

    private static final byte[] METADATA_GUID = {
            (byte)0xEA, (byte)0xCB, (byte)0xF8, (byte)0xC5, (byte)0xAF, 0x5B, 0x77, 0x48,
            (byte)0x84, 0x67, (byte)0xAA, (byte)0x8C, 0x44, (byte)0xFA, 0x4C, (byte)0xCA
    };

    private static final byte[] METADATA_LIBRARY_GUID = {
            (byte)0x94, 0x1C, 0x23, 0x44, (byte)0x98, (byte)0x94, (byte)0xD1, 0x49,
            (byte)0xA1, 0x41, 0x1D, 0x13, 0x4E, 0x45, 0x70, 0x54
    };

    private static final int GUID_SIZE = 16;
    private static final int ASF_HEADER_OBJECT_SIZE = 30;
    private static final int ASF_OBJECT_MIN_SIZE = 24;
    private static final int ASF_HEADER_EXT_DATA_OFFSET = 46;
    private static final int MAX_HEADER_OBJECTS = 10000;

    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.ASF_CONTENT_DESC, TagFormat.ASF_EXT_CONTENT_DESC);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (startBuffer.length < GUID_SIZE) {
            return false;
        }
        return Arrays.equals(Arrays.copyOfRange(startBuffer, 0, GUID_SIZE), ASF_HEADER_GUID);
    }

    @Override
    public List<TagInfo> detectTags(SeekableDataSource source, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        if (!canDetect(startBuffer, endBuffer)) {
            return tags;
        }

        LOG.debug("Detecting ASF tags in source: {}", source.name());

        try {
            ASFHeader asfHeader = parseASFHeader(source);
            if (asfHeader == null) {
                LOG.debug("Failed to parse ASF header");
                return tags;
            }

            LOG.debug("ASF Header: size={}, objects={}", asfHeader.headerSize, asfHeader.numHeaderObjects);

            long currentPos = ASF_HEADER_OBJECT_SIZE;
            long headerEnd = asfHeader.headerSize;

            for (int i = 0; i < asfHeader.numHeaderObjects && currentPos + ASF_OBJECT_MIN_SIZE < headerEnd; i++) {
                ASFObject object = parseASFObject(source, currentPos);

                if (object == null) {
                    LOG.warn("Failed to parse ASF object {} at position {}", i + 1, currentPos);
                    break;
                }

                TagInfo tagInfo = processASFObject(object, currentPos);
                if (tagInfo != null) {
                    tags.add(tagInfo);
                }

                if (Arrays.equals(object.guid, HEADER_EXT_GUID)) {
                    List<TagInfo> extTags = processHeaderExtensionObject(source, currentPos, object.size);
                    tags.addAll(extTags);
                }

                currentPos += object.size;
            }

            LOG.debug("ASF detection completed: found {} metadata objects", tags.size());

        } catch (IOException e) {
            LOG.error("Error detecting ASF tags in {}: {}", source.name(), e.getMessage());
            throw e;
        }

        return tags;
    }

    private ASFHeader parseASFHeader(SeekableDataSource source) throws IOException {
        byte[] headerGuid = new byte[GUID_SIZE];
        source.read(0, headerGuid, 0, GUID_SIZE);

        if (!Arrays.equals(headerGuid, ASF_HEADER_GUID)) {
            LOG.debug("Invalid ASF header GUID");
            return null;
        }

        byte[] sizeBytes = new byte[8];
        source.read(16, sizeBytes, 0, 8);
        long headerSize = readLittleEndianLong(sizeBytes, 0);

        byte[] countBytes = new byte[4];
        source.read(24, countBytes, 0, 4);
        int numHeaderObjects = readLittleEndianInt(countBytes, 0);

        byte[] reservedBytes = new byte[2];
        source.read(28, reservedBytes, 0, 2);

        if (headerSize < ASF_HEADER_OBJECT_SIZE || headerSize > source.length()) {
            LOG.warn("Invalid ASF header size: {}", headerSize);
            return null;
        }

        if (numHeaderObjects < 0 || numHeaderObjects > MAX_HEADER_OBJECTS) {
            LOG.warn("Invalid ASF header object count: {}", numHeaderObjects);
            return null;
        }

        return new ASFHeader(headerSize, numHeaderObjects);
    }

    private ASFObject parseASFObject(SeekableDataSource source, long offset) throws IOException {
        byte[] objectGuid = new byte[GUID_SIZE];
        source.read(offset, objectGuid, 0, GUID_SIZE);

        byte[] sizeBytes = new byte[8];
        source.read(offset + GUID_SIZE, sizeBytes, 0, 8);
        long objectSize = readLittleEndianLong(sizeBytes, 0);

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

    private List<TagInfo> processHeaderExtensionObject(SeekableDataSource source, long headerExtOffset,
                                                        long headerExtSize) {
        List<TagInfo> extTags = new ArrayList<>();

        try {
            long currentPos = headerExtOffset + ASF_OBJECT_MIN_SIZE;

            byte[] reserved1 = new byte[GUID_SIZE];
            source.read(headerExtOffset + ASF_OBJECT_MIN_SIZE, reserved1, 0, GUID_SIZE);
            currentPos += GUID_SIZE;

            byte[] reserved2Bytes = new byte[2];
            source.read(currentPos, reserved2Bytes, 0, 2);
            currentPos += 2;

            byte[] extDataSizeBytes = new byte[4];
            source.read(currentPos, extDataSizeBytes, 0, 4);
            int extDataSize = readLittleEndianInt(extDataSizeBytes, 0);
            currentPos += 4;

            if (extDataSize <= 0 || extDataSize > headerExtSize - ASF_HEADER_EXT_DATA_OFFSET) {
                LOG.debug("Invalid Header Extension data size: {}", extDataSize);
                return extTags;
            }

            long extDataStart = headerExtOffset + ASF_HEADER_EXT_DATA_OFFSET;
            long extDataEnd = extDataStart + extDataSize;

            while (currentPos + ASF_OBJECT_MIN_SIZE < extDataEnd) {
                ASFObject extObject = parseASFObject(source, currentPos);

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

    private int readLittleEndianShort(byte[] data, int offset) {
        return ((data[offset] & 0xFF)) | ((data[offset + 1] & 0xFF) << 8);
    }

    private int readLittleEndianInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF)) |
                ((data[offset + 1] & 0xFF) << 8) |
                ((data[offset + 2] & 0xFF) << 16) |
                ((data[offset + 3] & 0xFF) << 24);
    }

    private long readLittleEndianLong(byte[] data, int offset) {
        return ((long)(data[offset] & 0xFF)) |
                ((long)(data[offset + 1] & 0xFF) << 8) |
                ((long)(data[offset + 2] & 0xFF) << 16) |
                ((long)(data[offset + 3] & 0xFF) << 24) |
                ((long)(data[offset + 4] & 0xFF) << 32) |
                ((long)(data[offset + 5] & 0xFF) << 40) |
                ((long)(data[offset + 6] & 0xFF) << 48) |
                ((long)(data[offset + 7] & 0xFF) << 56);
    }

    private static class ASFHeader {
        final long headerSize;
        final int numHeaderObjects;

        ASFHeader(long headerSize, int numHeaderObjects) {
            this.headerSize = headerSize;
            this.numHeaderObjects = numHeaderObjects;
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