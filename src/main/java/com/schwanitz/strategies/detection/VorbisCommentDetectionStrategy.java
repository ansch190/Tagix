package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Detection Strategy for Vorbis Comments in OGG and FLAC files
 * <p>
 * Vorbis Comments are used in:
 * - OGG files: Embedded in Ogg pages with packet type 0x03
 * - FLAC files: As metadata block type 4
 * <p>
 * Structure varies by container but content format is standardized:
 * - Vendor string (length + UTF-8 text)
 * - Comment count (32-bit little-endian)
 * - Comments (length + UTF-8 key=value pairs)
 */
public class VorbisCommentDetectionStrategy extends TagDetectionStrategy {

    @Override
    public List<TagFormat> getSupportedFormats() {
        return List.of(TagFormat.VORBIS_COMMENT);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (startBuffer.length < 4) {
            return false;
        }
        String signature = new String(startBuffer, 0, 4);
        return signature.equals("OggS") || signature.equals("fLaC");
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        if (startBuffer.length < 4) {
            return tags;
        }
        String signature = new String(startBuffer, 0, 4);
        if (signature.equals("OggS")) {
            // Parse OGG structure to find a Vorbis Comment packet
            long position = 0;
            int pageCount = 0;
            while (position + 27 < file.length() && pageCount < 10) {
                file.seek(position);
                byte[] buffer = new byte[27];
                file.read(buffer);
                if (!new String(buffer, 0, 4).equals("OggS")) {
                    break;
                }
                int segmentCount = buffer[26] & 0xFF;
                byte[] segments = new byte[segmentCount];
                file.read(segments);
                int pageSize = 27 + segmentCount;
                for (byte segment : segments) {
                    pageSize += (segment & 0xFF);
                }
                long commentOffset = position + 27 + segmentCount;
                file.seek(commentOffset);
                byte[] packetType = new byte[1];
                file.read(packetType);
                if (packetType[0] == 3) {
                    tags.add(new TagInfo(TagFormat.VORBIS_COMMENT, commentOffset, pageSize - (commentOffset - position)));
                }
                position += pageSize;
                pageCount++;
            }
        } else if (signature.equals("fLaC")) {
            // Parse FLAC structure to find Vorbis Comment block
            long position = 4;
            while (position < file.length()) {
                file.seek(position);
                byte[] blockHeader = new byte[4];
                file.read(blockHeader);
                boolean isLast = (blockHeader[0] & 0x80) != 0;
                int blockType = blockHeader[0] & 0x7F;
                int blockLength = ((blockHeader[1] & 0xFF) << 16) | ((blockHeader[2] & 0xFF) << 8) | (blockHeader[3] & 0xFF);
                if (blockType == 4) {
                    tags.add(new TagInfo(TagFormat.VORBIS_COMMENT, position, blockLength + 4));
                }
                position += 4 + blockLength;
                if (isLast) {
                    break;
                }
            }
        }
        return tags;
    }
}