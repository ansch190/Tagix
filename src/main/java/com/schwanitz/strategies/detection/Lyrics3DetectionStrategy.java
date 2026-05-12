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

    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.LYRICS3V1, TagFormat.LYRICS3V2);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (endBuffer.length < LYRICS3_END_TAG_LENGTH) {
            return false;
        }
        String endTag = new String(endBuffer, endBuffer.length - LYRICS3_END_TAG_LENGTH, LYRICS3_END_TAG_LENGTH, StandardCharsets.US_ASCII);
        return endTag.equals("LYRICSEND") || endTag.equals("LYRICS200");
    }

    @Override
    public List<TagInfo> detectTags(SeekableDataSource source, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        if (endBuffer.length < LYRICS3_END_TAG_LENGTH) {
            return tags;
        }

        String endTag = new String(endBuffer, endBuffer.length - LYRICS3_END_TAG_LENGTH, LYRICS3_END_TAG_LENGTH, StandardCharsets.US_ASCII);
        if (endTag.equals("LYRICS200")) {
            tags.addAll(detectLyrics3v2(source, endBuffer));
        } else if (endTag.equals("LYRICSEND")) {
            tags.addAll(detectLyrics3v1(source));
        }
        return tags;
    }

    private List<TagInfo> detectLyrics3v2(SeekableDataSource source, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        long sourceLength = source.length();

        String sizeStr = readSizeFromEndBuffer(endBuffer);
        if (sizeStr == null) {
            sizeStr = readSizeFromSource(source, sourceLength);
        }

        if (sizeStr == null) {
            return tags;
        }

        try {
            int size = Integer.parseInt(sizeStr);
            long startOffset = sourceLength - LYRICS3V2_FOOTER_SIZE - size;
            if (startOffset >= 0) {
                byte[] buffer = new byte[LYRICS3_BEGIN_TAG_LENGTH];
                source.read(startOffset, buffer, 0, LYRICS3_BEGIN_TAG_LENGTH);
                if (new String(buffer, StandardCharsets.US_ASCII).equals("LYRICSBEGIN")) {
                    tags.add(new TagInfo(TagFormat.LYRICS3V2, startOffset, size + LYRICS3V2_FOOTER_SIZE));
                }
            }
        } catch (NumberFormatException e) {
            LOG.debug("Invalid Lyrics3v2 size indication: {}", sizeStr);
        }

        return tags;
    }

    private String readSizeFromEndBuffer(byte[] endBuffer) {
        if (endBuffer.length >= LYRICS3V2_FOOTER_SIZE) {
            return new String(endBuffer, endBuffer.length - LYRICS3V2_FOOTER_SIZE, LYRICS3V2_SIZE_LENGTH, StandardCharsets.US_ASCII);
        }
        return null;
    }

    private String readSizeFromSource(SeekableDataSource source, long sourceLength) throws IOException {
        byte[] sizeBytes = new byte[LYRICS3V2_FOOTER_SIZE];
        int bytesRead = source.read(sourceLength - LYRICS3V2_FOOTER_SIZE, sizeBytes, 0, LYRICS3V2_FOOTER_SIZE);
        if (bytesRead >= LYRICS3V2_SIZE_LENGTH) {
            return new String(sizeBytes, 0, LYRICS3V2_SIZE_LENGTH, StandardCharsets.US_ASCII);
        }
        return null;
    }

    private List<TagInfo> detectLyrics3v1(SeekableDataSource source) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        long sourceLength = source.length();
        long position = sourceLength - LYRICS3_END_TAG_LENGTH - LYRICS3_BEGIN_TAG_LENGTH;
        if (position >= 0) {
            byte[] buffer = new byte[LYRICS3_BEGIN_TAG_LENGTH];
            source.read(position, buffer, 0, LYRICS3_BEGIN_TAG_LENGTH);
            if (new String(buffer, StandardCharsets.US_ASCII).equals("LYRICSBEGIN")) {
                long tagSize = sourceLength - position;
                tags.add(new TagInfo(TagFormat.LYRICS3V1, position, tagSize));
            }
        }
        return tags;
    }
}