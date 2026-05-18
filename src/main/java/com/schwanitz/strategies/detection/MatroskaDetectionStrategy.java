package com.schwanitz.strategies.detection;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Erkennungsstrategie für Matroska/WebM-Container.
 * <p>
 * Matroska verwendet EBML (Extensible Binary Meta Language) als Containerformat.
 * WebM ist eine Teilmenge von Matroska speziell für Web-Video.
 * <p>
 * EBML-Elementstruktur:
 * <ul>
 *   <li>Element-ID: Variable-Length Integer (VLI)</li>
 *   <li>Element-Größe: Variable-Length Integer (VLI)</li>
 *   <li>Element-Inhalt: Größe in Bytes</li>
 * </ul>
 * <p>
 * Matroska-Struktur auf oberster Ebene:
 * <ul>
 *   <li>EBML-Header: 0x1A45DFA3 (immer erstes Element an Offset 0)</li>
 *   <li>Segment: 0x18538067 (folgt direkt nach EBML-Header)</li>
 *   <li>Das Segment enthält alle anderen Elemente (Info, Tracks, Tags usw.)</li>
 * </ul>
 * <p>
 * Wichtige Element-IDs:
 * <ul>
 *   <li>EBML: 0x1A45DFA3</li>
 *   <li>Segment: 0x18538067</li>
 *   <li>Info: 0x1549A966</li>
 *   <li>Tags: 0x1254C367</li>
 *   <li>Tag: 0x7373</li>
 *   <li>SimpleTag: 0x67C8</li>
 * </ul>
 */
public class MatroskaDetectionStrategy extends TagDetectionStrategy {

    private static final byte[] EBML_HEADER = {0x1A, 0x45, (byte)0xDF, (byte)0xA3};
    private static final byte[] SEGMENT_ID = {0x18, 0x53, (byte)0x80, 0x67};
    private static final byte[] TAGS_ID = {0x12, 0x54, (byte)0xC3, 0x67};

    private static final String DOCTYPE_MATROSKA = "matroska";
    private static final String DOCTYPE_WEBM = "webm";

    private static final int EBML_HEADER_ID_SIZE = 4;
    private static final int ELEMENT_ID_SIZE = 4;
    private static final int MIN_ELEMENT_SIZE = 8;
    private static final long MAX_EBML_HEADER_SIZE = 1000;

    private static final long EBML_DOCTYPE_ID = 0x4282L;
    private static final long EBML_TAGS_ELEMENT_ID = 0x1254C367L;
    private static final long EBML_VOID_ID = 0xEC;
    private static final long EBML_CRC32_ID = 0xBF;

    private static final int VLI_LEADING_BIT_MASK = 0x80;

    private static final int MAX_TOP_LEVEL_ELEMENTS = 50;

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
    public List<TagInfo> detectTags(SeekableDataSource source, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        if (!canDetect(startBuffer, endBuffer)) {
            return tags;
        }

        LOG.debug("Detecting Matroska/WebM tags in source: {}", source.name());

        try {
            byte[] ebmlHeaderId = new byte[EBML_HEADER_ID_SIZE];
            source.read(0, ebmlHeaderId, 0, EBML_HEADER_ID_SIZE);

            if (!Arrays.equals(ebmlHeaderId, EBML_HEADER)) {
                LOG.debug("Invalid EBML header");
                return tags;
            }

            long posAfterId = EBML_HEADER_ID_SIZE;
            long[] vliResult = readVLI(source, posAfterId);
            long ebmlHeaderSize = vliResult[0];
            long idSize = vliResult[1];

            if (ebmlHeaderSize <= 0 || ebmlHeaderSize > MAX_EBML_HEADER_SIZE) {
                LOG.warn("Invalid EBML header size: {}", ebmlHeaderSize);
                return tags;
            }

            LOG.debug("EBML Header size: {} bytes", ebmlHeaderSize);

            long headerContentStart = posAfterId + idSize;
            long ebmlHeaderEnd = headerContentStart + ebmlHeaderSize;

            String docType = extractDocType(source, headerContentStart, ebmlHeaderSize);
            TagFormat tagFormat = determineTagFormat(docType);

            if (tagFormat == null) {
                LOG.debug("Unknown or unsupported DocType: {}", docType);
                return tags;
            }

            LOG.debug("Detected container type: {} (DocType: {})", tagFormat.getFormatName(), docType);

            long segmentOffset = findSegmentAfterHeader(source, ebmlHeaderEnd);
            if (segmentOffset == -1) {
                LOG.debug("No Segment element found");
                return tags;
            }

            List<Long> tagOffsets = findTagsElements(source, segmentOffset);

            for (Long tagOffset : tagOffsets) {
                long[] vliResult2 = readVLI(source, tagOffset + ELEMENT_ID_SIZE);
                long tagsSize = vliResult2[0];
                long tagsSizeSize = vliResult2[1];
                long totalTagsSize = ELEMENT_ID_SIZE + tagsSizeSize + tagsSize;

                tags.add(new TagInfo(tagFormat, tagOffset, totalTagsSize));
                LOG.debug("Found {} Tags element at offset: {}, size: {} bytes",
                        tagFormat.getFormatName(), tagOffset, totalTagsSize);
            }

            if (tags.isEmpty()) {
                LOG.debug("No Tags elements found in Matroska/WebM source");
            }

        } catch (IOException e) {
            LOG.error("Error detecting Matroska/WebM tags in {}: {}", source.name(), e.getMessage());
            throw e;
        }

        return tags;
    }

    private String extractDocType(SeekableDataSource source, long headerContentStart, long headerSize) {
        long headerEnd = headerContentStart + headerSize;
        long currentPos = headerContentStart;

        while (currentPos < headerEnd) {
            try {
                long[] idResult = readVLI(source, currentPos);
                long elementId = idResult[0];
                long idSize = idResult[1];
                currentPos += idSize;

                long[] sizeResult = readVLI(source, currentPos);
                long elementSize = sizeResult[0];
                long sizeSize = sizeResult[1];
                currentPos += sizeSize;

                if (elementSize < 0 || elementSize > headerEnd - currentPos) {
                    break;
                }

                if (elementId == EBML_DOCTYPE_ID) {
                    byte[] docTypeBytes = new byte[(int)elementSize];
                    source.read(currentPos, docTypeBytes, 0, (int)elementSize);
                    return new String(docTypeBytes, StandardCharsets.UTF_8).trim();
                } else {
                    currentPos += elementSize;
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

    private static final long SEGMENT_ELEMENT_ID = 0x18538067L;

    private long findSegmentAfterHeader(SeekableDataSource source, long searchStart) throws IOException {
        long currentPos = searchStart;
        long sourceLength = source.length();

        for (int i = 0; i < MAX_TOP_LEVEL_ELEMENTS && currentPos < sourceLength; i++) {
            long[] idResult = readVLI(source, currentPos);
            long elementId = idResult[0];
            long idSize = idResult[1];

            long[] sizeResult = readVLI(source, currentPos + idSize);
            long elementSize = sizeResult[0];
            long sizeSize = sizeResult[1];

            if (elementId == SEGMENT_ELEMENT_ID) {
                return currentPos;
            }

            if (elementId == EBML_VOID_ID || elementId == EBML_CRC32_ID) {
                if (elementSize > 0) {
                    currentPos += idSize + sizeSize + elementSize;
                } else {
                    currentPos++;
                }
                continue;
            }

            if (elementSize < 0) {
                LOG.debug("Invalid element size at offset {}, stopping scan", currentPos);
                break;
            }

            currentPos += idSize + sizeSize + elementSize;
        }

        return -1;
    }

    private List<Long> findTagsElements(SeekableDataSource source, long segmentOffset) {
        List<Long> tagOffsets = new ArrayList<>();

        try {
            long[] sizeResult = readVLI(source, segmentOffset + ELEMENT_ID_SIZE);
            long segmentSize = sizeResult[0];
            long segmentSizeSize = sizeResult[1];

            if (segmentSize <= 0) {
                return tagOffsets;
            }

            long segmentDataStart = segmentOffset + ELEMENT_ID_SIZE + segmentSizeSize;
            long segmentEnd = segmentDataStart + segmentSize;
            long currentPos = segmentDataStart;

            while (currentPos + MIN_ELEMENT_SIZE < segmentEnd) {
                try {
                    long[] idResult = readVLI(source, currentPos);
                    long currentElementId = idResult[0];
                    long idSize = idResult[0] != 0 ? idResult[1] : 0;

                    long[] elementSizeResult = readVLI(source, currentPos + idResult[1]);
                    long elementSize = elementSizeResult[0];
                    long sizeSize = elementSizeResult[1];

                    if (elementSize < 0 || elementSize > segmentEnd - currentPos - idResult[1] - sizeSize) {
                        break;
                    }

                    if (currentElementId == EBML_TAGS_ELEMENT_ID) {
                        tagOffsets.add(currentPos);
                        LOG.debug("Found Tags element at offset: {}, size: {} bytes",
                                currentPos, elementSize);
                    }

                    currentPos = currentPos + idResult[1] + sizeSize + elementSize;

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

    /**
     * Liest einen VLI (Variable Length Integer) Wert aus der Datenquelle.
     * Gibt ein long[2] zurück: [0] = der gelesene Wert, [1] = die Anzahl der bytes die der VLI belegt.
     */
    private long[] readVLI(SeekableDataSource source, long offset) throws IOException {
        byte[] firstByteBuf = new byte[1];
        source.read(offset, firstByteBuf, 0, 1);
        int firstByte = firstByteBuf[0] & 0xFF;
        if (firstByte == -1) {
            throw new IOException("Unexpected end of source while reading VLI");
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

        if (length > 1) {
            byte[] remainingBytes = new byte[length - 1];
            source.read(offset + 1, remainingBytes, 0, length - 1);
            for (int i = 0; i < length - 1; i++) {
                value = (value << 8) | (remainingBytes[i] & 0xFF);
            }
        }

        return new long[]{value, length};
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

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }
}