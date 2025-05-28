package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

public class ID3V2DetectionStrategy extends TagDetectionStrategy {

    @Override
    public List<TagFormat> getSupportedFormats() {
        return List.of(TagFormat.ID3V2_2, TagFormat.ID3V2_3, TagFormat.ID3V2_4);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (startBuffer.length < 10) {
            return false;
        }
        return new String(startBuffer, 0, 3).equals("ID3");
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        if (canDetect(startBuffer, endBuffer)) {
            int majorVersion = startBuffer[3] & 0xFF;
            int revision = startBuffer[4] & 0xFF;
            if (revision != 0) {
                Log.debug("Invalid Revision for ID3v2: {}", revision);
                return tags;
            }
            TagFormat format;
            switch (majorVersion) {
                case 2:
                    format = TagFormat.ID3V2_2;
                    // Size for ID3v2.2: 3 Bytes (24-Bit)
                    int sizeV2 = ((startBuffer[6] & 0xFF) << 16) | ((startBuffer[7] & 0xFF) << 8) | (startBuffer[8] & 0xFF);
                    tags.add(new TagInfo(format, 0, sizeV2 + 10));
                    break;
                case 3:
                    format = TagFormat.ID3V2_3;
                    // Size for ID3v2.3: 4 Bytes (synchsafe)
                    int sizeV3 = ((startBuffer[6] & 0x7F) << 21) | ((startBuffer[7] & 0x7F) << 14) |
                            ((startBuffer[8] & 0x7F) << 7) | (startBuffer[9] & 0x7F);
                    tags.add(new TagInfo(format, 0, sizeV3 + 10));
                    break;
                case 4:
                    format = TagFormat.ID3V2_4;
                    // Size for ID3v2.4: 4 Bytes (synchsafe)
                    int sizeV4 = ((startBuffer[6] & 0x7F) << 21) | ((startBuffer[7] & 0x7F) << 14) |
                            ((startBuffer[8] & 0x7F) << 7) | (startBuffer[9] & 0x7F);
                    tags.add(new TagInfo(format, 0, sizeV4 + 10));
                    break;
                default:
                    Log.debug("Unknown ID3v2-Version: {}", majorVersion);
                    break;
            }
        }
        return tags;
    }
}