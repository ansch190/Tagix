package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

public class ID3V2_3DetectionStrategy extends TagDetectionStrategy {

    @Override
    public TagFormat getTagFormat() {
        return TagFormat.ID3V2_3;
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (startBuffer.length < 10) {
            return false;
        }
        if (!new String(startBuffer, 0, 3).equals("ID3")) {
            return false;
        }
        int majorVersion = startBuffer[3] & 0xFF;
        int revision = startBuffer[4] & 0xFF;
        return majorVersion == 3 && revision == 0;
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        if (canDetect(startBuffer, endBuffer)) {
            int size = ((startBuffer[6] & 0x7F) << 21) | ((startBuffer[7] & 0x7F) << 14) |
                    ((startBuffer[8] & 0x7F) << 7) | (startBuffer[9] & 0x7F);
            tags.add(new TagInfo(TagFormat.ID3V2_3, 0, size + 10));
        }
        return tags;
    }
}