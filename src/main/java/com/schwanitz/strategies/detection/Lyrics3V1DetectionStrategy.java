package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

public class Lyrics3V1DetectionStrategy extends TagDetectionStrategy {

    @Override
    public TagFormat getTagFormat() {
        return TagFormat.LYRICS3V1;
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (endBuffer.length < 9) {
            return false;
        }
        return new String(endBuffer, endBuffer.length - 9, 9).equals("LYRICSEND");
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        if (canDetect(startBuffer, endBuffer)) {
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