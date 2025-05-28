package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

public class APEV2DetectionStrategy extends TagDetectionStrategy {

    private static final int APE_HEADER_SIZE = 32;

    @Override
    public TagFormat getTagFormat() {
        return TagFormat.APEV2;
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        return checkAPE(startBuffer, 0) || checkAPE(endBuffer, endBuffer.length - APE_HEADER_SIZE);
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        if (checkAPE(startBuffer, 0)) {
            int tagSize = getAPETagSize(startBuffer, 0);
            tags.add(new TagInfo(TagFormat.APEV2, 0, tagSize));
        }
        if (checkAPE(endBuffer, endBuffer.length - APE_HEADER_SIZE)) {
            int tagSize = getAPETagSize(endBuffer, endBuffer.length - APE_HEADER_SIZE);
            long offset = file.length() - tagSize;
            tags.add(new TagInfo(TagFormat.APEV2, offset, tagSize));
        }
        return tags;
    }

    private boolean checkAPE(byte[] buffer, int offset) {
        if (offset + APE_HEADER_SIZE > buffer.length) {
            return false;
        }
        return new String(buffer, offset, 8).equals("APETAGEX") &&
                buffer[offset + 8] == (byte) 0xD0 && buffer[offset + 9] == 0x07;
    }

    private int getAPETagSize(byte[] buffer, int offset) {
        return ((buffer[offset + 12] & 0xFF)) | ((buffer[offset + 13] & 0xFF) << 8) |
                ((buffer[offset + 14] & 0xFF) << 16) | ((buffer[offset + 15] & 0xFF) << 24);
    }
}