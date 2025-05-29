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
 * Detection Strategy for Matroska/WebM Container
 * <p>
 * Matroska uses EBML (Extensible Binary Meta Language) as container format.
 * WebM is a subset of Matroska specifically for web video.
 * <p>
 * EBML Structure:
 * - EBML Header: 0x1A45DFA3 (4 bytes)
 * - DocType: "matroska" or "webm"
 * - Segment: 0x18538067 (4 bytes)
 * - Tags Element: 0x1254C367 (4 bytes) - contains metadata
 * <p>
 * Important Element IDs:
 * - EBML: 0x1A45DFA3
 * - Segment: 0x18538067
 * - Info: 0x1549A966
 * - Tags: 0x1254C367
 * - Tag: 0x7373
 * - SimpleTag: 0x67C8
 */
public class MatroskaDetectionStrategy extends TagDetectionStrategy {

    // EBML Element IDs (Variable Length Integers)
    private static final byte[] EBML_HEADER = {0x1A, 0x45, (byte)0xDF, (byte)0xA3};
    private static final byte[] SEGMENT_ID = {0x18, 0x53, (byte)0x80, 0x67};
    private static final byte[] TAGS_ID = {0x12, 0x54, (byte)0xC3, 0x67};
    private static final byte[] INFO_ID = {0x15, 0x49, (byte)0xA9, 0x66};

    // DocType Strings
    private static final String DOCTYPE_MATROSKA = "matroska";
    private static final String DOCTYPE_WEBM = "webm";

    // EBML Element Type Mapping
    private static final Map<String, String> EBML_ELEMENTS = new HashMap<>();

    static {
        EBML_ELEMENTS.put("1A45DFA3", "EBML Header");
        EBML_ELEMENTS.put("18538067", "Segment");
        EBML_ELEMENTS.put("1549A966", "Info");
        EBML_ELEMENTS.put("1254C367", "Tags");
        EBML_ELEMENTS.put("7373", "Tag");
        EBML_ELEMENTS.put("67C8", "SimpleTag");
        EBML_ELEMENTS.put("45A3", "TagName");
        EBML_ELEMENTS.put("4487", "TagString");
        EBML_ELEMENTS.put("68CA", "TagDefault");
        EBML_ELEMENTS.put("6484", "TagLanguage");
    }

    @Override
    public List<TagFormat> getSupportedFormats() {
        return List.of(TagFormat.MATROSKA_TAGS, TagFormat.WEBM_TAGS);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (startBuffer.length < 4) {
            return false;
        }

        // Check EBML Header: 0x1A45DFA3
        return Arrays.equals(Arrays.copyOfRange(startBuffer, 0, 4), EBML_HEADER);
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath,
                                    byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        if (!canDetect(startBuffer, endBuffer)) {
            return tags;
        }

        Log.debug("Detecting Matroska/WebM tags in file: {}", filePath);

        try {
            file.seek(0);

            // Validate EBML Header
            byte[] ebmlHeaderId = new byte[4];
            file.read(ebmlHeaderId);

            if (!Arrays.equals(ebmlHeaderId, EBML_HEADER)) {
                Log.debug("Invalid EBML header");
                return tags;
            }

            // Read EBML Header Size (Variable Length Integer)
            long ebmlHeaderSize = readVLI(file);
            if (ebmlHeaderSize <= 0 || ebmlHeaderSize > 1000) {
                Log.warn("Invalid EBML header size: {}", ebmlHeaderSize);
                return tags;
            }

            Log.debug("EBML Header size: {} bytes", ebmlHeaderSize);

            // Extract DocType from EBML Header
            String docType = extractDocType(file, ebmlHeaderSize);
            TagFormat tagFormat = determineTagFormat(docType);

            if (tagFormat == null) {
                Log.debug("Unknown or unsupported DocType: {}", docType);
                return tags;
            }

            Log.debug("Detected container type: {} (DocType: {})", tagFormat.getFormatName(), docType);

            // Find Segment
            long segmentOffset = findSegment(file);
            if (segmentOffset == -1) {
                Log.debug("No Segment element found");
                return tags;
            }

            // Find Tags Element in Segment
            List<Long> tagOffsets = findTagsElements(file, segmentOffset);

            for (Long tagOffset : tagOffsets) {
                // Determine Tags Element Size
                file.seek(tagOffset + 4); // After Tags ID
                long tagsSize = readVLI(file);
                long totalTagsSize = 4 + getVLISize(tagsSize) + tagsSize;

                tags.add(new TagInfo(tagFormat, tagOffset, totalTagsSize));
                Log.debug("Found {} Tags element at offset: {}, size: {} bytes",
                        tagFormat.getFormatName(), tagOffset, totalTagsSize);
            }

            if (tags.isEmpty()) {
                Log.debug("No Tags elements found in Matroska/WebM file");
            }

        } catch (IOException e) {
            Log.error("Error detecting Matroska/WebM tags in {}: {}", filePath, e.getMessage());
            throw e;
        }

        return tags;
    }

    /**
     * Extract DocType from EBML Header
     */
    private String extractDocType(RandomAccessFile file, long headerSize) throws IOException {
        long headerStart = file.getFilePointer();
        long headerEnd = headerStart + headerSize;

        while (file.getFilePointer() < headerEnd) {
            try {
                // Read Element ID (VLI)
                long elementId = readVLI(file);

                // Read Element Size (VLI)
                long elementSize = readVLI(file);

                if (elementSize < 0 || elementSize > headerEnd - file.getFilePointer()) {
                    break;
                }

                // DocType Element ID: 0x4282
                if (elementId == 0x4282) {
                    byte[] docTypeBytes = new byte[(int)elementSize];
                    file.read(docTypeBytes);
                    return new String(docTypeBytes, StandardCharsets.UTF_8).trim();
                } else {
                    // Skip element
                    file.skipBytes((int)elementSize);
                }

            } catch (IOException e) {
                Log.debug("Error parsing EBML header element: {}", e.getMessage());
                break;
            }
        }

        return "unknown";
    }

    /**
     * Determine TagFormat based on DocType
     */
    private TagFormat determineTagFormat(String docType) {
        return switch (docType.toLowerCase()) {
            case DOCTYPE_MATROSKA -> TagFormat.MATROSKA_TAGS;
            case DOCTYPE_WEBM -> TagFormat.WEBM_TAGS;
            default -> null;
        };
    }

    /**
     * Find Segment Element after EBML Header
     */
    private long findSegment(RandomAccessFile file) throws IOException {
        // After EBML Header, we search for the Segment
        long currentPos = file.getFilePointer();
        long fileLength = file.length();

        while (currentPos + 4 < fileLength) {
            file.seek(currentPos);

            byte[] elementId = new byte[4];
            int bytesRead = file.read(elementId);

            if (bytesRead != 4) {
                break;
            }

            if (Arrays.equals(elementId, SEGMENT_ID)) {
                return currentPos;
            }

            currentPos++;
        }

        return -1;
    }

    /**
     * Find Tags Elements in Segment
     */
    private List<Long> findTagsElements(RandomAccessFile file, long segmentOffset) throws IOException {
        List<Long> tagOffsets = new ArrayList<>();

        try {
            file.seek(segmentOffset + 4); // After Segment ID
            long segmentSize = readVLI(file);

            if (segmentSize <= 0) {
                return tagOffsets;
            }

            long segmentDataStart = file.getFilePointer();
            long segmentEnd = segmentDataStart + segmentSize;
            long currentPos = segmentDataStart;

            // Search Segment (only first level-1 elements)
            while (currentPos + 8 < segmentEnd) {
                file.seek(currentPos);

                try {
                    // Read Element ID
                    long currentElementId = readVLI(file);
                    long elementSize = readVLI(file);

                    if (elementSize < 0 || elementSize > segmentEnd - file.getFilePointer()) {
                        break;
                    }

                    // Tags Element ID: 0x1254C367 (as long)
                    if (currentElementId == 0x1254C367L) {
                        tagOffsets.add(currentPos);
                        Log.debug("Found Tags element at offset: {}, size: {} bytes",
                                currentPos, elementSize);
                    }

                    // Jump to next element
                    currentPos = file.getFilePointer() + elementSize;

                } catch (IOException e) {
                    Log.debug("Error parsing segment element at {}: {}", currentPos, e.getMessage());
                    break;
                }
            }

        } catch (IOException e) {
            Log.debug("Error parsing segment: {}", e.getMessage());
        }

        return tagOffsets;
    }

    /**
     * Read EBML Variable Length Integer
     */
    private long readVLI(RandomAccessFile file) throws IOException {
        int firstByte = file.read();
        if (firstByte == -1) {
            throw new IOException("Unexpected end of file while reading VLI");
        }

        // Determine the number of bytes based on leading zeros
        int length = 0;
        int mask = 0x80;

        for (int i = 0; i < 8; i++) {
            if ((firstByte & mask) != 0) {
                length = i + 1;
                break;
            }
            mask >>= 1;
        }

        if (length == 0) {
            throw new IOException("Invalid VLI: no length marker found");
        }

        // Compose value
        long value = firstByte & (0xFF >> length);

        for (int i = 1; i < length; i++) {
            int nextByte = file.read();
            if (nextByte == -1) {
                throw new IOException("Incomplete VLI");
            }
            value = (value << 8) | nextByte;
        }

        return value;
    }

    /**
     * Determine the byte count of VLI based on its value
     */
    private int getVLISize(long value) {
        if (value < 0x7F) return 1;
        if (value < 0x3FFF) return 2;
        if (value < 0x1FFFFF) return 3;
        if (value < 0x0FFFFFFF) return 4;
        if (value < 0x07FFFFFFFFL) return 5;
        if (value < 0x03FFFFFFFFFFL) return 6;
        if (value < 0x01FFFFFFFFFFFFL) return 7;
        return 8;
    }

    /**
     * Convert bytes to hex string for debugging
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }
}