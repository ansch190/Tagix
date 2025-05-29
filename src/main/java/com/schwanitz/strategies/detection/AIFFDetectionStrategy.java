package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Detection Strategy for AIFF metadata chunks
 * <p>
 * AIFF (Audio Interchange File Format) uses IFF chunk structure.
 * Metadata is stored in various chunk types:
 * - NAME: Title/name information
 * - AUTH: Author/artist information
 * - ANNO: Annotation/comment
 * - (c) : Copyright information
 * - COMT: Structured comment chunks
 * <p>
 * Structure: FORM header + chunk type + chunks
 */
public class AIFFDetectionStrategy extends TagDetectionStrategy {

    @Override
    public List<TagFormat> getSupportedFormats() {
        return List.of(TagFormat.AIFF_METADATA);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        return startBuffer.length >= 12 && new String(startBuffer, 0, 4).equals("FORM") &&
                (new String(startBuffer, 8, 4).equals("AIFF") || new String(startBuffer, 8, 4).equals("AIFC"));
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
                int chunkSize = ((chunkHeader[4] & 0xFF) << 24) | ((chunkHeader[5] & 0xFF) << 16) |
                        ((chunkHeader[6] & 0xFF) << 8) | (chunkHeader[7] & 0xFF);
                if (chunkType.equals("NAME") || chunkType.equals("AUTH") || chunkType.equals("ANNO") || chunkType.equals("(c) ")) {
                    tags.add(new TagInfo(TagFormat.AIFF_METADATA, position, chunkSize + 8));
                }
                position += 8 + chunkSize;
            }
        }
        return tags;
    }
}