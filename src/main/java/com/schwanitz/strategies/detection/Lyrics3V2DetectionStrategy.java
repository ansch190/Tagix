package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

public class Lyrics3V2DetectionStrategy extends TagDetectionStrategy {

    @Override
    public TagFormat getTagFormat() {
        return TagFormat.LYRICS3V2;
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (endBuffer.length < 15) {
            return false;
        }
        return new String(endBuffer, endBuffer.length - 15, 9).equals("LYRICS200");
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        if (canDetect(startBuffer, endBuffer)) {
            String sizeStr = new String(endBuffer, endBuffer.length - 15, 6);
            try {
                int size = Integer.parseInt(sizeStr);
                long startOffset = file.length() - 15 - size;
                file.seek(startOffset);
                byte[] buffer = new byte[11];
                file.read(buffer);
                if (new String(buffer).equals("LYRICSBEGIN")) {
                    tags.add(new TagInfo(TagFormat.LYRICS3V2, startOffset, size + 15));
                }
            } catch (NumberFormatException e) {
                Log.debug("Ungültige Lyrics3v2-Größe: " + sizeStr);
            }
        }
        return tags;
    }
}