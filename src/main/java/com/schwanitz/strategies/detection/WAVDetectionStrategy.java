package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Detection Strategy for WAV file metadata
 * <p>
 * Supports multiple metadata formats in WAV files:
 * - RIFF INFO: Standard WAV metadata in LIST/INFO chunk
 * - BWF (Broadcast Wave Format): Professional audio metadata in bext chunk
 *   - BWF v0: Basic broadcast metadata
 *   - BWF v1: Extended with coding history
 *   - BWF v2: Loudness information added
 * <p>
 * WAV structure: RIFF header + chunks (fmt, data, bext, LIST, etc.)
 */
public class WAVDetectionStrategy extends TagDetectionStrategy {

    @Override
    public List<TagFormat> getSupportedFormats() {
        return List.of(TagFormat.BWF_V0, TagFormat.BWF_V1, TagFormat.BWF_V2, TagFormat.RIFF_INFO);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (startBuffer.length < 12) {
            return false;
        }
        return new String(startBuffer, 0, 4).equals("RIFF") && new String(startBuffer, 8, 4).equals("WAVE");
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        if (!canDetect(startBuffer, endBuffer)) {
            return tags;
        }

        long position = 12; // Skip RIFF header
        while (position + 8 < file.length()) {
            file.seek(position);
            byte[] chunkHeader = new byte[8];
            file.read(chunkHeader);
            String chunkType = new String(chunkHeader, 0, 4);
            int chunkSize = ((chunkHeader[4] & 0xFF)) | ((chunkHeader[5] & 0xFF) << 8) |
                    ((chunkHeader[6] & 0xFF) << 16) | ((chunkHeader[7] & 0xFF) << 24);

            if (chunkSize < 0 || chunkSize > file.length() - position - 8) {
                Log.debug("Invalid ChunkSize: {} at Position {}", chunkSize, position);
                break;
            }

            if (chunkType.equals("LIST")) {
                file.seek(position + 8);
                byte[] listType = new byte[4];
                file.read(listType);
                if (new String(listType).equals("INFO")) {
                    tags.add(new TagInfo(TagFormat.RIFF_INFO, position, chunkSize + 8));
                }
            } else if (chunkType.equals("bext")) {
                if (chunkSize >= 602) {
                    // Determine BWF version from version field
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
                            Log.debug("Unknown BWF-Version: {}", version);
                            break;
                    }
                    if (format != null) {
                        tags.add(new TagInfo(format, position, chunkSize + 8));
                    }
                }
            }

            position += 8 + chunkSize;
            if (chunkSize % 2 != 0) {
                position++; // Padding byte for even alignment
            }
        }

        return tags;
    }
}