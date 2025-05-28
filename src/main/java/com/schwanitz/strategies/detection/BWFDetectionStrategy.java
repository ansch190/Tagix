package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

public class BWFDetectionStrategy extends TagDetectionStrategy {

    @Override
    public TagFormat getTagFormat() {
        return TagFormat.BWF_V0; // Standardwert, wird dynamisch angepasst
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
                if (chunkType.equals("bext")) {
                    if (chunkSize >= 602) {
                        file.seek(position + 8 + 602);
                        byte[] versionBuffer = new byte[2];
                        file.read(versionBuffer);
                        int version = ((versionBuffer[0] & 0xFF)) | ((versionBuffer[1] & 0xFF) << 8);
                        TagFormat format;
                        switch (version) {
                            case 0:
                                format = TagFormat.BWF_V0;
                                break;
                            case 1:
                                format = TagFormat.BWF_V1;
                                break;
                            case 2:
                                format = TagFormat.BWF_V2;
                                break;
                            default:
                                format = null;
                                break;
                        }
                        if (format != null) {
                            tags.add(new TagInfo(format, position, chunkSize + 8));
                        }
                    }
                }
                position += 8 + chunkSize;
            }
        }
        return tags;
    }
}