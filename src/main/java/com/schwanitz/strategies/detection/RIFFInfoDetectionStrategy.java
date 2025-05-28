package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

public class RIFFInfoDetectionStrategy extends TagDetectionStrategy {

    @Override
    public TagFormat getTagFormat() {
        return TagFormat.RIFF_INFO;
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        return startBuffer.length >= 12 && new String(startBuffer, 0, 4).equals("RIFF") &&
                new String(startBuffer, 8, 4).equals("WAVE");
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        if (canDetect(startBuffer, endBuffer)) {
            long position = 12;
            while (position + 8 < file.length()) {
                file.seek(position);
                byte[] chunkHeader = new byte[8];
                file.read(chunkHeader);
                String chunkType = new String(chunkHeader, 0, 4);
                int chunkSize = ((chunkHeader[4] & 0xFF)) | ((chunkHeader[5] & 0xFF) << 8) |
                        ((chunkHeader[6] & 0xFF) << 16) | ((chunkHeader[7] & 0xFF) << 24);
                if (chunkType.equals("LIST")) {
                    file.seek(position + 8);
                    byte[] listType = new byte[4];
                    file.read(listType);
                    if (new String(listType).equals("INFO")) {
                        tags.add(new TagInfo(TagFormat.RIFF_INFO, position, chunkSize + 8));
                    }
                }
                position += 8 + chunkSize;
            }
        }
        return tags;
    }
}