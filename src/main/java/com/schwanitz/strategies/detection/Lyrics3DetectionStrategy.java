package com.schwanitz.strategies.detection;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Erkennungsstrategie für Lyrics3-Tags (Versionen 1 und 2).
 * <p>
 * Lyrics3-Tags befinden sich am Ende von Dateien, vor etwaigen ID3v1-Tags.
 * <p>
 * Lyrics3v1:
 * <ul>
 *   <li>Start: "LYRICSBEGIN"</li>
 *   <li>Liedtext-Inhalt (variable Länge)</li>
 *   <li>Ende: "LYRICSEND"</li>
 * </ul>
 * <p>
 * Lyrics3v2:
 * <ul>
 *   <li>Start: "LYRICSBEGIN"</li>
 *   <li>Feldinhalt mit Größeninformationen</li>
 *   <li>Größenangabe (6 Ziffern)</li>
 *   <li>Ende: "LYRICS200"</li>
 * </ul>
 */
public class Lyrics3DetectionStrategy extends TagDetectionStrategy {

    private static final int LYRICS3_END_TAG_LENGTH = 9;
    private static final int LYRICS3_BEGIN_TAG_LENGTH = 11;
    private static final int LYRICS3V2_SIZE_LENGTH = 6;
    private static final int LYRICS3V2_FOOTER_SIZE = LYRICS3_END_TAG_LENGTH + LYRICS3V2_SIZE_LENGTH;
    private static final int ID3V1_TAG_SIZE = 128;

    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.LYRICS3V1, TagFormat.LYRICS3V2);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (endBuffer.length < LYRICS3_END_TAG_LENGTH) {
            return false;
        }
        if (checkLyrics3Footer(endBuffer, endBuffer.length)) {
            return true;
        }
        if (endBuffer.length > ID3V1_TAG_SIZE + LYRICS3_END_TAG_LENGTH && hasID3v1Marker(endBuffer)) {
            return checkLyrics3Footer(endBuffer, endBuffer.length - ID3V1_TAG_SIZE);
        }
        return false;
    }

    @Override
    public List<TagInfo> detectTags(SeekableDataSource source, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        if (endBuffer.length < LYRICS3_END_TAG_LENGTH) {
            return tags;
        }

        boolean hasID3v1 = endBuffer.length > ID3V1_TAG_SIZE && hasID3v1Marker(endBuffer);

        String endTag = getLyrics3EndTag(endBuffer, endBuffer.length);
        if (!isLyrics3EndTag(endTag) && hasID3v1) {
            endTag = getLyrics3EndTag(endBuffer, endBuffer.length - ID3V1_TAG_SIZE);
        }

        if ("LYRICS200".equals(endTag)) {
            tags.addAll(detectLyrics3v2(source, endBuffer, hasID3v1));
        } else if ("LYRICSEND".equals(endTag)) {
            tags.addAll(detectLyrics3v1(source, hasID3v1));
        }
        return tags;
    }

    private boolean checkLyrics3Footer(byte[] endBuffer, int effectiveEnd) {
        if (effectiveEnd < LYRICS3_END_TAG_LENGTH) {
            return false;
        }
        String tag = new String(endBuffer, effectiveEnd - LYRICS3_END_TAG_LENGTH, LYRICS3_END_TAG_LENGTH, StandardCharsets.US_ASCII);
        return tag.equals("LYRICSEND") || tag.equals("LYRICS200");
    }

    private String getLyrics3EndTag(byte[] endBuffer, int effectiveEnd) {
        if (effectiveEnd < LYRICS3_END_TAG_LENGTH) {
            return null;
        }
        return new String(endBuffer, effectiveEnd - LYRICS3_END_TAG_LENGTH, LYRICS3_END_TAG_LENGTH, StandardCharsets.US_ASCII);
    }

    private boolean isLyrics3EndTag(String tag) {
        return "LYRICS200".equals(tag) || "LYRICSEND".equals(tag);
    }

    private boolean hasID3v1Marker(byte[] endBuffer) {
        int offset = endBuffer.length - ID3V1_TAG_SIZE;
        if (offset < 0) return false;
        return endBuffer[offset] == 'T' && endBuffer[offset + 1] == 'A' && endBuffer[offset + 2] == 'G';
    }

    private List<TagInfo> detectLyrics3v2(SeekableDataSource source, byte[] endBuffer, boolean hasID3v1) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        long sourceLength = source.length();
        long effectiveEnd = hasID3v1 ? sourceLength - ID3V1_TAG_SIZE : sourceLength;

        String sizeStr = readSizeFromEndBuffer(endBuffer, hasID3v1);
        if (sizeStr == null) {
            sizeStr = readSizeFromSource(source, effectiveEnd);
        }

        if (sizeStr == null) {
            return tags;
        }

        try {
            int size = Integer.parseInt(sizeStr);
            long startOffset = effectiveEnd - LYRICS3V2_FOOTER_SIZE - size;
            if (startOffset >= 0) {
                byte[] buffer = new byte[LYRICS3_BEGIN_TAG_LENGTH];
                source.read(startOffset, buffer, 0, LYRICS3_BEGIN_TAG_LENGTH);
                if (new String(buffer, StandardCharsets.US_ASCII).equals("LYRICSBEGIN")) {
                    long totalSize = size + LYRICS3V2_FOOTER_SIZE;
                    if (hasID3v1) {
                        totalSize += ID3V1_TAG_SIZE;
                    }
                    tags.add(new TagInfo(TagFormat.LYRICS3V2, startOffset, totalSize));
                }
            }
        } catch (NumberFormatException e) {
            LOG.debug("Invalid Lyrics3v2 size indication: {}", sizeStr);
        }

        return tags;
    }

    private String readSizeFromEndBuffer(byte[] endBuffer, boolean hasID3v1) {
        int effectiveEnd = endBuffer.length;
        if (hasID3v1) {
            effectiveEnd -= ID3V1_TAG_SIZE;
        }
        if (effectiveEnd >= LYRICS3V2_FOOTER_SIZE) {
            int offset = effectiveEnd - LYRICS3V2_FOOTER_SIZE;
            return new String(endBuffer, offset, LYRICS3V2_SIZE_LENGTH, StandardCharsets.US_ASCII);
        }
        return null;
    }

    private String readSizeFromSource(SeekableDataSource source, long effectiveEnd) throws IOException {
        byte[] sizeBytes = new byte[LYRICS3V2_FOOTER_SIZE];
        int bytesRead = source.read(effectiveEnd - LYRICS3V2_FOOTER_SIZE, sizeBytes, 0, LYRICS3V2_FOOTER_SIZE);
        if (bytesRead >= LYRICS3V2_SIZE_LENGTH) {
            return new String(sizeBytes, 0, LYRICS3V2_SIZE_LENGTH, StandardCharsets.US_ASCII);
        }
        return null;
    }

    private List<TagInfo> detectLyrics3v1(SeekableDataSource source, boolean hasID3v1) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        long sourceLength = source.length();
        long effectiveEnd = hasID3v1 ? sourceLength - ID3V1_TAG_SIZE : sourceLength;

        if (effectiveEnd < LYRICS3_END_TAG_LENGTH) {
            return tags;
        }

        long lyricoEndOffset = effectiveEnd - LYRICS3_END_TAG_LENGTH;
        byte[] endTagBuf = new byte[LYRICS3_BEGIN_TAG_LENGTH];
        long searchFrom = Math.max(0, lyricoEndOffset - 8192);
        byte[] scanBuf = new byte[(int) Math.min(lyricoEndOffset - searchFrom + LYRICS3_BEGIN_TAG_LENGTH, 8192 + LYRICS3_BEGIN_TAG_LENGTH)];
        int scanRead = source.read(searchFrom, scanBuf, 0, scanBuf.length);
        if (scanRead < LYRICS3_BEGIN_TAG_LENGTH) return tags;

        int beginOffset = -1;
        for (int i = scanRead - LYRICS3_BEGIN_TAG_LENGTH; i >= 0; i--) {
            boolean match = true;
            for (int j = 0; j < LYRICS3_BEGIN_TAG_LENGTH; j++) {
                if (scanBuf[i + j] != "LYRICSBEGIN".getBytes(StandardCharsets.US_ASCII)[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                beginOffset = i;
                break;
            }
        }

        if (beginOffset >= 0) {
            long absoluteOffset = searchFrom + beginOffset;
            long tagSize = effectiveEnd - absoluteOffset;
            tags.add(new TagInfo(TagFormat.LYRICS3V1, absoluteOffset, tagSize));
        }
        return tags;
    }
}