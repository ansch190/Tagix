package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Detection Strategy for APE tags (versions 1.0 and 2.0)
 * <p>
 * APE tags can be located at the beginning or end of files.
 * Structure:
 * - Preamble: "APETAGEX" (8 bytes)
 * - Version: 4 bytes little-endian (1000 for v1.0, 2000 for v2.0)
 * - Tag Size: 4 bytes little-endian (size without header)
 * - Item Count: 4 bytes little-endian
 * - Flags: 4 bytes little-endian
 * - Reserved: 8 bytes
 * <p>
 * APE v2.0 can have both header and footer, while v1.0 only has footer.
 * The flags indicate the presence of header/footer and read-only status.
 */
public class APEDetectionStrategy extends TagDetectionStrategy {

    private static final int APE_HEADER_SIZE = 32;

    @Override
    public List<TagFormat> getSupportedFormats() {
        return List.of(TagFormat.APEV1, TagFormat.APEV2);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        return checkAPE(startBuffer, 0) || checkAPE(endBuffer, endBuffer.length - APE_HEADER_SIZE);
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        // Check at the beginning of the File
        if (checkAPE(startBuffer, 0)) {
            int version = getAPEVersion(startBuffer, 0);
            int tagSize = getAPETagSize(startBuffer, 0);
            TagFormat format = getAPEFormat(version);
            if (format != null) {
                tags.add(new TagInfo(format, 0, tagSize));
            }
        }

        // Check at the end of the File
        if (checkAPE(endBuffer, endBuffer.length - APE_HEADER_SIZE)) {
            int version = getAPEVersion(endBuffer, endBuffer.length - APE_HEADER_SIZE);
            int tagSize = getAPETagSize(endBuffer, endBuffer.length - APE_HEADER_SIZE);
            TagFormat format = getAPEFormat(version);
            if (format != null) {
                long offset = file.length() - tagSize;
                tags.add(new TagInfo(format, offset, tagSize));
            }
        }

        return tags;
    }

    /**
     * Check for APE signature at specified buffer offset
     */
    private boolean checkAPE(byte[] buffer, int offset) {
        if (offset + APE_HEADER_SIZE > buffer.length) {
            return false;
        }
        return new String(buffer, offset, 8).equals("APETAGEX");
    }

    /**
     * Extract an APE version from buffer
     */
    private int getAPEVersion(byte[] buffer, int offset) {
        return ((buffer[offset + 8] & 0xFF)) | ((buffer[offset + 9] & 0xFF) << 8) |
                ((buffer[offset + 10] & 0xFF) << 16) | ((buffer[offset + 11] & 0xFF) << 24);
    }

    /**
     * Extract APE tag size from buffer
     */
    private int getAPETagSize(byte[] buffer, int offset) {
        return ((buffer[offset + 12] & 0xFF)) | ((buffer[offset + 13] & 0xFF) << 8) |
                ((buffer[offset + 14] & 0xFF) << 16) | ((buffer[offset + 15] & 0xFF) << 24);
    }

    /**
     * Determine TagFormat based on version number
     */
    private TagFormat getAPEFormat(int version) {
        if (version == 1000) {
            return TagFormat.APEV1;
        } else if (version == 2000) {
            return TagFormat.APEV2;
        } else {
            Log.debug("Unknown APE-Version: {}", version);
            return null;
        }
    }
}