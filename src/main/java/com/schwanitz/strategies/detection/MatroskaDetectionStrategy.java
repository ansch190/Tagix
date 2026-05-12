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
 * EBML element structure:
 * - Element ID: Variable Length Integer (VLI)
 * - Element Size: Variable Length Integer (VLI)
 * - Element Content: size bytes
 * <p>
 * Top-level Matroska structure:
 * - EBML Header: 0x1A45DFA3 (always first element at offset 0)
 * - Segment: 0x18538067 (immediately follows EBML header)
 * - The Segment contains all other elements (Info, Tracks, Tags, etc.)
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

    // EBML Element IDs
    private static final byte[] EBML_HEADER = {0x1A, 0x45, (byte)0xDF, (byte)0xA3};
    private static final byte[] SEGMENT_ID = {0x18, 0x53, (byte)0x80, 0x67};
    private static final byte[] TAGS_ID = {0x12, 0x54, (byte)0xC3, 0x67};

    // DocType Strings
    private static final String DOCTYPE_MATROSKA = "matroska";
    private static final String DOCTYPE_WEBM = "webm";

    // EBML structural constants
    private static final int EBML_HEADER_ID_SIZE = 4;
    private static final int ELEMENT_ID_SIZE = 4;
    private static final int MIN_ELEMENT_SIZE = 8;
    private static final long MAX_EBML_HEADER_SIZE = 1000;

    // EBML Element IDs (numeric for VLI comparison)
    private static final long EBML_DOCTYPE_ID = 0x4282L;
    private static final long EBML_TAGS_ELEMENT_ID = 0x1254C367L;
    private static final long EBML_VOID_ID = 0xEC;
    private static final long EBML_CRC32_ID = 0xBF;

    // VLI leading bit mask
    private static final int VLI_LEADING_BIT_MASK = 0x80;

    // Maximum top-level elements to scan before giving up
    private static final int MAX_TOP_LEVEL_ELEMENTS = 50;

    // EBML Element Type Mapping (for debugging)
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
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.MATROSKA_TAGS, TagFormat.WEBM_TAGS);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (startBuffer.length < EBML_HEADER_ID_SIZE) {
            return false;
        }

        return Arrays.equals(Arrays.copyOfRange(startBuffer, 0, EBML_HEADER_ID_SIZE), EBML_HEADER);
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath,
                                    byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        if (!canDetect(startBuffer, endBuffer)) {
            return tags;
        }

        LOG.debug("Detecting Matroska/WebM tags in file: {}", filePath);

        try {
            file.seek(0);

            byte[] ebmlHeaderId = new byte[EBML_HEADER_ID_SIZE];
            file.read(ebmlHeaderId);

            if (!Arrays.equals(ebmlHeaderId, EBML_HEADER)) {
                LOG.debug("Invalid EBML header");
                return tags;
            }

            long ebmlHeaderSize = readVLI(file);
            if (ebmlHeaderSize <= 0 || ebmlHeaderSize > MAX_EBML_HEADER_SIZE) {
                LOG.warn("Invalid EBML header size: {}", ebmlHeaderSize);
                return tags;
            }

            LOG.debug("EBML Header size: {} bytes", ebmlHeaderSize);

            // Calculate the end of the EBML header element
            long headerContentStart = file.getFilePointer();
            long ebmlHeaderEnd = headerContentStart + ebmlHeaderSize;

            String docType = extractDocType(file, headerContentStart, ebmlHeaderSize);
            TagFormat tagFormat = determineTagFormat(docType);

            if (tagFormat == null) {
                LOG.debug("Unknown or unsupported DocType: {}", docType);
                return tags;
            }

            LOG.debug("Detected container type: {} (DocType: {})", tagFormat.getFormatName(), docType);

            // Seek directly to the end of the EBML header and scan top-level elements
            long segmentOffset = findSegmentAfterHeader(file, ebmlHeaderEnd);
            if (segmentOffset == -1) {
                LOG.debug("No Segment element found");
                return tags;
            }

            List<Long> tagOffsets = findTagsElements(file, segmentOffset);

            for (Long tagOffset : tagOffsets) {
                file.seek(tagOffset + ELEMENT_ID_SIZE);
                long tagsSize = readVLI(file);
                long totalTagsSize = ELEMENT_ID_SIZE + getVLISize(tagsSize) + tagsSize;

                tags.add(new TagInfo(tagFormat, tagOffset, totalTagsSize));
                LOG.debug("Found {} Tags element at offset: {}, size: {} bytes",
                        tagFormat.getFormatName(), tagOffset, totalTagsSize);
            }

            if (tags.isEmpty()) {
                LOG.debug("No Tags elements found in Matroska/WebM file");
            }

        } catch (IOException e) {
            LOG.error("Error detecting Matroska/WebM tags in {}: {}", filePath, e.getMessage());
            throw e;
        }

        return tags;
    }

    /**
     * Extract DocType from EBML Header by seeking to the content area.
     */
    private String extractDocType(RandomAccessFile file, long headerContentStart, long headerSize) throws IOException {
        long headerEnd = headerContentStart + headerSize;

        file.seek(headerContentStart);

        while (file.getFilePointer() < headerEnd) {
            try {
                long elementId = readVLI(file);
                long elementSize = readVLI(file);

                if (elementSize < 0 || elementSize > headerEnd - file.getFilePointer()) {
                    break;
                }

                if (elementId == EBML_DOCTYPE_ID) {
                    byte[] docTypeBytes = new byte[(int)elementSize];
                    file.read(docTypeBytes);
                    return new String(docTypeBytes, StandardCharsets.UTF_8).trim();
                } else {
                    file.skipBytes((int)elementSize);
                }

            } catch (IOException e) {
                LOG.debug("Error parsing EBML header element: {}", e.getMessage());
                break;
            }
        }

        return "unknown";
    }

    private TagFormat determineTagFormat(String docType) {
        return switch (docType.toLowerCase()) {
            case DOCTYPE_MATROSKA -> TagFormat.MATROSKA_TAGS;
            case DOCTYPE_WEBM -> TagFormat.WEBM_TAGS;
            default -> null;
        };
    }

    // Segment element ID as long (0x18538067)
    private static final long SEGMENT_ELEMENT_ID = 0x18538067L;

    /**
     * Find the Segment element by scanning top-level elements starting
     * from the end of the EBML header. This is O(k) where k is the
     * number of top-level elements before Segment (typically 1).
     */
    private long findSegmentAfterHeader(RandomAccessFile file, long searchStart) throws IOException {
        long currentPos = searchStart;
        long fileLength = file.length();

        for (int i = 0; i < MAX_TOP_LEVEL_ELEMENTS && currentPos < fileLength; i++) {
            file.seek(currentPos);

            long elementId = readVLI(file);
            long idSize = getVLISize(elementId);
            long elementSize = readVLI(file);
            long sizeSize = getVLISize(elementSize);

            if (elementId == SEGMENT_ELEMENT_ID) {
                return currentPos;
            }

            // Void or CRC elements: skip them
            if (elementId == EBML_VOID_ID || elementId == EBML_CRC32_ID) {
                if (elementSize > 0) {
                    currentPos += idSize + sizeSize + elementSize;
                } else {
                    // Size 0 or unknown: can't skip, try next byte position
                    currentPos++;
                }
                continue;
            }

            // Not the Segment and not skipable metadata
            if (elementSize < 0) {
                LOG.debug("Invalid element size at offset {}, stopping scan", currentPos);
                break;
            }

            // Jump to next top-level element
            currentPos += idSize + sizeSize + elementSize;
        }

        return -1;
    }

    private List<Long> findTagsElements(RandomAccessFile file, long segmentOffset) throws IOException {
        List<Long> tagOffsets = new ArrayList<>();

        try {
            file.seek(segmentOffset + ELEMENT_ID_SIZE);
            long segmentSize = readVLI(file);

            if (segmentSize <= 0) {
                return tagOffsets;
            }

            long segmentDataStart = file.getFilePointer();
            long segmentEnd = segmentDataStart + segmentSize;
            long currentPos = segmentDataStart;

            while (currentPos + MIN_ELEMENT_SIZE < segmentEnd) {
                file.seek(currentPos);

                try {
                    long currentElementId = readVLI(file);
                    long elementSize = readVLI(file);

                    if (elementSize < 0 || elementSize > segmentEnd - file.getFilePointer()) {
                        break;
                    }

                    if (currentElementId == EBML_TAGS_ELEMENT_ID) {
                        tagOffsets.add(currentPos);
                        LOG.debug("Found Tags element at offset: {}, size: {} bytes",
                                currentPos, elementSize);
                    }

                    // Jump to next element
                    currentPos = file.getFilePointer() + elementSize;

                } catch (IOException e) {
                    LOG.debug("Error parsing segment element at {}: {}", currentPos, e.getMessage());
                    break;
                }
            }

        } catch (IOException e) {
            LOG.debug("Error parsing segment: {}", e.getMessage());
        }

        return tagOffsets;
    }

    private long readVLI(RandomAccessFile file) throws IOException {
        int firstByte = file.read();
        if (firstByte == -1) {
            throw new IOException("Unexpected end of file while reading VLI");
        }

        int length = 0;
        int mask = VLI_LEADING_BIT_MASK;

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

    private int getVLISizeForId(long elementId) {
        if (elementId < 0xFF) return 1;
        if (elementId < 0x3FFF) return 2;
        if (elementId < 0x1FFFFF) return 3;
        return 4; // Most EBML/Matroska IDs are 4 bytes
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }
}