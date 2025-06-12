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
 * Detection Strategy for ASF (Advanced Systems Format) files
 * <p>
 * ASF is used for .wma, .asf, .wmv files with Windows Media metadata.
 * Structure:
 * - ASF Header Object GUID: 75B22630-668E-11CF-A6D9-00AA0062CE6C
 * - Header Object Size (8 bytes little-endian)
 * - Number of Header Objects (4 bytes)
 * - Reserved (2 bytes)
 * <p>
 * Key metadata objects:
 * - Content Description: Basic metadata (title, author, etc.)
 * - Extended Content Description: Extended metadata fields
 * - Metadata Object: Additional metadata
 * - Metadata Library Object: Large metadata collections
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

    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.ASF_CONTENT_DESC, TagFormat.ASF_EXT_CONTENT_DESC);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (startBuffer.length < 16) {
            return false;
        }
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
            // Parse ASF header
            ASFHeader asfHeader = parseASFHeader(file);
            if (asfHeader == null) {
                Log.debug("Failed to parse ASF header");
                return tags;
            }

            Log.debug("ASF Header: size={}, objects={}", asfHeader.headerSize, asfHeader.numHeaderObjects);

            // Search header objects for metadata
            long currentPos = 30; // After ASF Header (16 + 8 + 4 + 2)
            long headerEnd = asfHeader.headerSize;

            for (int i = 0; i < asfHeader.numHeaderObjects && currentPos + 24 < headerEnd; i++) {
                ASFObject object = parseASFObject(file, currentPos);

                if (object == null) {
                    Log.warn("Failed to parse ASF object {} at position {}", i + 1, currentPos);
                    break;
                }

                TagInfo tagInfo = processASFObject(object, currentPos);
                if (tagInfo != null) {
                    tags.add(tagInfo);
                }

                // Process Header Extension Objects for additional metadata
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
     * Parse ASF header structure
     */
    private ASFHeader parseASFHeader(RandomAccessFile file) throws IOException {
        file.seek(0);

        byte[] headerGuid = new byte[16];
        file.read(headerGuid);

        if (!Arrays.equals(headerGuid, ASF_HEADER_GUID)) {
            Log.debug("Invalid ASF header GUID");
            return null;
        }

        long headerSize = readLittleEndianLong(file);
        int numHeaderObjects = readLittleEndianInt(file);
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

        return new ASFHeader(headerSize, numHeaderObjects, reserved);
    }

    /**
     * Parse individual ASF object
     */
    private ASFObject parseASFObject(RandomAccessFile file, long offset) throws IOException {
        file.seek(offset);

        byte[] objectGuid = new byte[16];
        file.read(objectGuid);

        long objectSize = readLittleEndianLong(file);

        if (objectSize < 24) {
            Log.warn("Invalid ASF object size: {} at offset: {}", objectSize, offset);
            return null;
        }

        return new ASFObject(objectGuid, objectSize);
    }

    /**
     * Process an ASF object and create TagInfo if it contains metadata
     */
    private TagInfo processASFObject(ASFObject object, long offset) {
        if (Arrays.equals(object.guid, CONTENT_DESC_GUID)) {
            Log.debug("Found ASF Content Description Object at offset: {}, size: {} bytes", offset, object.size);
            return new TagInfo(TagFormat.ASF_CONTENT_DESC, offset, object.size);
        }

        if (Arrays.equals(object.guid, EXT_CONTENT_DESC_GUID)) {
            Log.debug("Found ASF Extended Content Description Object at offset: {}, size: {} bytes", offset, object.size);
            return new TagInfo(TagFormat.ASF_EXT_CONTENT_DESC, offset, object.size);
        }

        if (Arrays.equals(object.guid, METADATA_GUID) || Arrays.equals(object.guid, METADATA_LIBRARY_GUID)) {
            Log.debug("Found ASF Metadata Object at offset: {}, size: {} bytes", offset, object.size);
            return new TagInfo(TagFormat.ASF_EXT_CONTENT_DESC, offset, object.size);
        }

        return null;
    }

    /**
     * Process Header Extension Objects for additional metadata
     */
    private List<TagInfo> processHeaderExtensionObject(RandomAccessFile file, long headerExtOffset,
                                                       long headerExtSize) throws IOException {
        List<TagInfo> extTags = new ArrayList<>();

        try {
            file.seek(headerExtOffset + 24); // Skip GUID + Size

            byte[] reserved1 = new byte[16];
            file.read(reserved1);

            int reserved2 = readLittleEndianShort(file);
            int extDataSize = readLittleEndianInt(file);

            if (extDataSize <= 0 || extDataSize > headerExtSize - 46) {
                Log.debug("Invalid Header Extension data size: {}", extDataSize);
                return extTags;
            }

            long extDataStart = headerExtOffset + 46;
            long extDataEnd = extDataStart + extDataSize;
            long currentPos = extDataStart;

            // Search extension objects for metadata
            while (currentPos + 24 < extDataEnd) {
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
            Log.debug("Error parsing ASF Header Extension: {}", e.getMessage());
        }

        return extTags;
    }

    /**
     * Read 16-bit little-endian short
     */
    private int readLittleEndianShort(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[2];
        file.read(bytes);
        return ((bytes[0] & 0xFF)) | ((bytes[1] & 0xFF) << 8);
    }

    /**
     * Read 32-bit little-endian integer
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
     * Read 64-bit little-endian long
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
     * ASF Header data class
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
     * ASF Object data class
     */
    private static class ASFObject {
        final byte[] guid;
        final long size;

        ASFObject(byte[] guid, long size) {
            this.guid = guid;
            this.size = size;
        }
    }
}