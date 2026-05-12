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
 * - Size: 4 bytes (synchsafe integer in all versions)
 * <p>
 * The tag size field is encoded as a synchsafe integer in ALL ID3v2 versions
 * (2.2, 2.3, and 2.4), per the ID3v2 specification. Each byte only uses 7 bits,
 * giving a maximum tag size of 256 MB.
 * <p>
 * Version differences apply to frame structure:
 * - ID3v2.2: 3-character frame IDs, 24-bit plain frame sizes
 * - ID3v2.3: 4-character frame IDs, 32-bit plain frame sizes
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

            int tagSize = decodeSynchsafeInt(startBuffer[6], startBuffer[7], startBuffer[8], startBuffer[9]);

            TagFormat format = switch (majorVersion) {
                case 2 -> TagFormat.ID3V2_2;
                case 3 -> TagFormat.ID3V2_3;
                case 4 -> TagFormat.ID3V2_4;
                default -> {
                    LOG.debug("Unknown ID3v2-Version: {}", majorVersion);
                    yield null;
                }
            };

            if (format != null) {
                tags.add(new TagInfo(format, 0, tagSize + ID3V2_HEADER_SIZE));
            }
        }
        return tags;
    }

    /**
     * Decode a 4-byte synchsafe integer as used in ID3v2 tag headers.
     * Each byte only uses 7 bits (MSB is always 0).
     * Maximum value: 2^28 - 1 = 268435455 (256 MB - 1)
     */
    private int decodeSynchsafeInt(byte b0, byte b1, byte b2, byte b3) {
        return ((b0 & SYNCHSAFE_MASK) << 21) |
                ((b1 & SYNCHSAFE_MASK) << 14) |
                ((b2 & SYNCHSAFE_MASK) << 7) |
                (b3 & SYNCHSAFE_MASK);
    }
}