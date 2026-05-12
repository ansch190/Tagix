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
    private static final int APE_PREAMBLE_LENGTH = 8;
    private static final int APE_VERSION_1 = 1000;
    private static final int APE_VERSION_2 = 2000;

    // APE header field offsets (relative to start of header)
    private static final int APE_VERSION_OFFSET = 8;
    private static final int APE_TAG_SIZE_OFFSET = 12;

    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.APEV1, TagFormat.APEV2);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        return checkAPE(startBuffer, 0) || checkAPE(endBuffer, endBuffer.length - APE_HEADER_SIZE);
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        if (checkAPE(startBuffer, 0)) {
            int version = getAPEVersion(startBuffer, 0);
            int tagSize = getAPETagSize(startBuffer, 0);
            TagFormat format = getAPEFormat(version);
            if (format != null) {
                tags.add(new TagInfo(format, 0, tagSize));
            }
        }

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

    private boolean checkAPE(byte[] buffer, int offset) {
        if (offset + APE_HEADER_SIZE > buffer.length) {
            return false;
        }
        return new String(buffer, offset, APE_PREAMBLE_LENGTH, StandardCharsets.US_ASCII).equals("APETAGEX");
    }

    private int getAPEVersion(byte[] buffer, int offset) {
        return ((buffer[offset + APE_VERSION_OFFSET] & 0xFF)) | ((buffer[offset + APE_VERSION_OFFSET + 1] & 0xFF) << 8) |
                ((buffer[offset + APE_VERSION_OFFSET + 2] & 0xFF) << 16) | ((buffer[offset + APE_VERSION_OFFSET + 3] & 0xFF) << 24);
    }

    private int getAPETagSize(byte[] buffer, int offset) {
        return ((buffer[offset + APE_TAG_SIZE_OFFSET] & 0xFF)) | ((buffer[offset + APE_TAG_SIZE_OFFSET + 1] & 0xFF) << 8) |
                ((buffer[offset + APE_TAG_SIZE_OFFSET + 2] & 0xFF) << 16) | ((buffer[offset + APE_TAG_SIZE_OFFSET + 3] & 0xFF) << 24);
    }

    private TagFormat getAPEFormat(int version) {
        if (version == APE_VERSION_1) {
            return TagFormat.APEV1;
        } else if (version == APE_VERSION_2) {
            return TagFormat.APEV2;
        } else {
            LOG.debug("Unknown APE-Version: {}", version);
            return null;
        }
    }
}