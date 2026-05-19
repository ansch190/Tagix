package com.schwanitz.strategies.detection;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Erkennungsstrategie für APE-Tags (Versionen 1.0 und 2.0).
 * <p>
 * APE-Tags können am Anfang oder am Ende von Dateien stehen.
 * Struktur:
 * <ul>
 *   <li>Präambel: "APETAGEX" (8 Bytes)</li>
 *   <li>Version: 4 Bytes Little-Endian (1000 für v1.0, 2000 für v2.0)</li>
 *   <li>Tag-Größe: 4 Bytes Little-Endian (Größe ohne Header)</li>
 *   <li>Elementanzahl: 4 Bytes Little-Endian</li>
 *   <li>Flags: 4 Bytes Little-Endian</li>
 *   <li>Reserviert: 8 Bytes</li>
 * </ul>
 * <p>
 * APE v2.0 kann sowohl Header als auch Footer haben, während v1.0 nur einen Footer besitzt.
 * Die Flags geben das Vorhandensein von Header/Footer und den Schreibschutz-Status an.
 */
public class APEDetectionStrategy extends TagDetectionStrategy {

    private static final int APE_HEADER_SIZE = 32;
    private static final int APE_PREAMBLE_LENGTH = 8;
    private static final int APE_VERSION_1 = 1000;
    private static final int APE_VERSION_2 = 2000;

    private static final int APE_VERSION_OFFSET = 8;
    private static final int APE_TAG_SIZE_OFFSET = 12;

    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.APEV1, TagFormat.APEV2);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        return checkAPE(startBuffer, 0) || (endBuffer.length >= APE_HEADER_SIZE && checkAPE(endBuffer, endBuffer.length - APE_HEADER_SIZE));
    }

    @Override
    public List<TagInfo> detectTags(SeekableDataSource source, byte[] startBuffer, byte[] endBuffer) throws IOException {
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
                long fileLength = source.length();
                if (tagSize < 0 || tagSize > fileLength) {
                    LOG.warn("Invalid APE tag size {} for file length {}", tagSize, fileLength);
                    return tags;
                }
                long offset = fileLength - tagSize;
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