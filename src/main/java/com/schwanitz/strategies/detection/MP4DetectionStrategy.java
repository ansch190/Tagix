package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Detection Strategy for MP4 container metadata
 * <p>
 * MP4 files use atom-based structure with metadata in:
 * moov -> udta -> meta -> ilst
 * <p>
 * Key atoms:
 * - ftyp: File type identifier (at file start)
 * - moov: Movie atom containing metadata
 * - udta: User data atom
 * - meta: Metadata atom
 * - ilst: Item list atom (contains actual metadata)
 */
public class MP4DetectionStrategy extends TagDetectionStrategy {

    @Override
    public List<TagFormat> getSupportedFormats() {
        return List.of(TagFormat.MP4);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        return startBuffer.length >= 8 && new String(startBuffer, 4, 4).equals("ftyp");
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        if (canDetect(startBuffer, endBuffer)) {
            long position = 0;
            while (position + 8 < file.length()) {
                file.seek(position);
                byte[] atomHeader = new byte[8];
                file.read(atomHeader);
                int atomSize = ((atomHeader[0] & 0xFF) << 24) | ((atomHeader[1] & 0xFF) << 16) |
                        ((atomHeader[2] & 0xFF) << 8) | (atomHeader[3] & 0xFF);
                String atomType = new String(atomHeader, 4, 4);
                if (atomType.equals("moov")) {
                    tags.add(new TagInfo(TagFormat.MP4, position, atomSize));
                    break;
                }
                position += atomSize;
            }
        }
        return tags;
    }
}