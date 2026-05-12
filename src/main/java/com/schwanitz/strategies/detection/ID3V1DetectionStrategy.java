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
 * Erkennungsstrategie für ID3v1- und ID3v1.1-Tags.
 * <p>
 * ID3v1-Tags befinden sich immer an den letzten 128 Bytes einer Datei.
 * Struktur:
 * <ul>
 *   <li>Kennzeichen: "TAG" (3 Bytes)</li>
 *   <li>Titel: 30 Bytes</li>
 *   <li>Interpret: 30 Bytes</li>
 *   <li>Album: 30 Bytes</li>
 *   <li>Jahr: 4 Bytes</li>
 *   <li>Kommentar: 30 Bytes (ID3v1) oder 28 Bytes + Titelnummer (ID3v1.1)</li>
 *   <li>Genre: 1 Byte</li>
 * </ul>
 * <p>
 * ID3v1.1 wird von ID3v1 durch ein Null-Byte an Position 125 gefolgt von
 * der Titelnummer an Position 126 unterschieden.
 */
public class ID3V1DetectionStrategy extends TagDetectionStrategy {

    private static final int ID3V1_SIZE = 128;

    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.ID3V1, TagFormat.ID3V1_1);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (endBuffer.length < ID3V1_SIZE) {
            return false;
        }
        return new String(endBuffer, endBuffer.length - ID3V1_SIZE, 3, StandardCharsets.US_ASCII).equals("TAG");
    }

    @Override
    public List<TagInfo> detectTags(SeekableDataSource source, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        if (canDetect(startBuffer, endBuffer)) {
            long offset = source.length() - ID3V1_SIZE;
            int trackOffset = endBuffer.length - ID3V1_SIZE + 125;
            if (endBuffer[trackOffset] == 0 && endBuffer[trackOffset + 1] != 0) {
                tags.add(new TagInfo(TagFormat.ID3V1_1, offset, ID3V1_SIZE));
            } else {
                tags.add(new TagInfo(TagFormat.ID3V1, offset, ID3V1_SIZE));
            }
        }
        return tags;
    }
}