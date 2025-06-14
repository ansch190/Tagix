package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Detection Strategy for Lyrics3 tags (versions 1 and 2)
 * <p>
 * Lyrics3 tags are located at the end of files, before ID3v1 tags.
 * <p>
 * Lyrics3v1:
 * - Start: "LYRICSBEGIN"
 * - Lyrics content (variable length)
 * - End: "LYRICSEND"
 * <p>
 * Lyrics3v2:
 * - Start: "LYRICSBEGIN"
 * - Field content with size information
 * - Size indication (6 digits)
 * - End: "LYRICS200"
 */
public class Lyrics3DetectionStrategy extends TagDetectionStrategy {

    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.LYRICS3V1, TagFormat.LYRICS3V2);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (endBuffer.length < 9) {
            return false;
        }
        String endTag = new String(endBuffer, endBuffer.length - 9, 9);
        return endTag.equals("LYRICSEND") || endTag.equals("LYRICS200");
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        if (endBuffer.length < 9) {
            return tags;
        }

        String endTag = new String(endBuffer, endBuffer.length - 9, 9);
        if (endTag.equals("LYRICS200")) {
            // Lyrics3v2: Check size indication and start signature
            if (endBuffer.length < 15) {
                return tags;
            }
            String sizeStr = new String(endBuffer, endBuffer.length - 15, 6);
            try {
                int size = Integer.parseInt(sizeStr);
                long startOffset = file.length() - 15 - size;
                if (startOffset >= 0) {
                    file.seek(startOffset);
                    byte[] buffer = new byte[11];
                    file.read(buffer);
                    if (new String(buffer).equals("LYRICSBEGIN")) {
                        tags.add(new TagInfo(TagFormat.LYRICS3V2, startOffset, size + 15));
                    }
                }
            } catch (NumberFormatException e) {
                Log.debug("Invalid Lyrics3v2 size indication: {}", sizeStr);
            }
        } else if (endTag.equals("LYRICSEND")) {
            // Lyrics3v1: Search for "LYRICSBEGIN"
            long position = file.length() - 9 - 11;
            if (position >= 0) {
                file.seek(position);
                byte[] buffer = new byte[11];
                file.read(buffer);
                if (new String(buffer).equals("LYRICSBEGIN")) {
                    long tagSize = file.length() - position;
                    tags.add(new TagInfo(TagFormat.LYRICS3V1, position, tagSize));
                }
            }
        }
        return tags;
    }
}