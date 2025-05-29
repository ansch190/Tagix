package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Detection Strategy for ID3v1 and ID3v1.1 tags
 * <p>
 * ID3v1 tags are always located at the last 128 bytes of a file.
 * Structure:
 * - Header: "TAG" (3 bytes)
 * - Title: 30 bytes
 * - Artist: 30 bytes
 * - Album: 30 bytes
 * - Year: 4 bytes
 * - Comment: 30 bytes (ID3v1) or 28 bytes + track number (ID3v1.1)
 * - Genre: 1 byte
 * <p>
 * ID3v1.1 is distinguished by a zero byte at position 125 followed by
 * the track number at position 126.
 */
public class ID3V1DetectionStrategy extends TagDetectionStrategy {

    private static final int ID3V1_SIZE = 128;

    @Override
    public List<TagFormat> getSupportedFormats() {
        return List.of(TagFormat.ID3V1, TagFormat.ID3V1_1);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (endBuffer.length < ID3V1_SIZE) {
            return false;
        }
        return new String(endBuffer, endBuffer.length - ID3V1_SIZE, 3).equals("TAG");
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        if (canDetect(startBuffer, endBuffer)) {
            long offset = file.length() - ID3V1_SIZE;
            int trackOffset = endBuffer.length - ID3V1_SIZE + 125;
            if (endBuffer[trackOffset] == 0 && endBuffer[trackOffset + 1] != 0) {
                tags.add(new TagInfo(TagFormat.ID3V1_1, offset, ID3V1_SIZE));
            } else {
                tags.add(new TagInfo(TagFormat.ID3V1, offset, ID3V1_SIZE));
            }
        }
        return tags;
    }
}