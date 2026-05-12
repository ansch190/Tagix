package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Detection Strategy for ID3v2 tags (versions 2.2, 2.3, and 2.4)
 * <p>
 * ID3v2 tags are located at the beginning of files and have the structure:
 * - Header: "ID3" (3 bytes)
 * - Major Version: 1 byte (2, 3, or 4)
 * - Minor Version: 1 byte (always 0)
 * - Flags: 1 byte
 * - Size: 4 bytes (synchsafe integer for v2.3/2.4, normal integer for v2.2)
 * <p>
 * Version differences:
 * - ID3v2.2: 3-character frame IDs, 24-bit frame sizes
 * - ID3v2.3: 4-character frame IDs, 32-bit frame sizes
 * - ID3v2.4: 4-character frame IDs, synchsafe frame sizes, UTF-8 support
 */
public class ID3V2DetectionStrategy extends TagDetectionStrategy {

    private static final int ID3V2_HEADER_SIZE = 10;
    private static final int ID3V2_SIGNATURE_LENGTH = 3;
    private static final int SYNCHSAFE_MASK = 0x7F;

    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.ID3V2_2, TagFormat.ID3V2_3, TagFormat.ID3V2_4);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (startBuffer.length < ID3V2_HEADER_SIZE) {
            return false;
        }
        return new String(startBuffer, 0, ID3V2_SIGNATURE_LENGTH, StandardCharsets.US_ASCII).equals("ID3");
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        if (canDetect(startBuffer, endBuffer)) {
            int majorVersion = startBuffer[3] & 0xFF;
            int revision = startBuffer[4] & 0xFF;
            if (revision != 0) {
                LOG.debug("Invalid Revision for ID3v2: {}", revision);
                return tags;
            }
            TagFormat format;
            switch (majorVersion) {
                case 2:
                    format = TagFormat.ID3V2_2;
                    int sizeV2 = ((startBuffer[6] & 0xFF) << 16) | ((startBuffer[7] & 0xFF) << 8) | (startBuffer[8] & 0xFF);
                    tags.add(new TagInfo(format, 0, sizeV2 + ID3V2_HEADER_SIZE));
                    break;
                case 3:
                    format = TagFormat.ID3V2_3;
                    int sizeV3 = ((startBuffer[6] & SYNCHSAFE_MASK) << 21) | ((startBuffer[7] & SYNCHSAFE_MASK) << 14) |
                            ((startBuffer[8] & SYNCHSAFE_MASK) << 7) | (startBuffer[9] & SYNCHSAFE_MASK);
                    tags.add(new TagInfo(format, 0, sizeV3 + ID3V2_HEADER_SIZE));
                    break;
                case 4:
                    format = TagFormat.ID3V2_4;
                    int sizeV4 = ((startBuffer[6] & SYNCHSAFE_MASK) << 21) | ((startBuffer[7] & SYNCHSAFE_MASK) << 14) |
                            ((startBuffer[8] & SYNCHSAFE_MASK) << 7) | (startBuffer[9] & SYNCHSAFE_MASK);
                    tags.add(new TagInfo(format, 0, sizeV4 + ID3V2_HEADER_SIZE));
                    break;
                default:
                    LOG.debug("Unknown ID3v2-Version: {}", majorVersion);
                    break;
            }
        }
        return tags;
    }
}