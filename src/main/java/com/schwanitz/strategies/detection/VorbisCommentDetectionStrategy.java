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
 * <p>
 * Uses hybrid approach for OGG: direct jump to a comment packet with fallback to sequential search
 */
public class VorbisCommentDetectionStrategy extends TagDetectionStrategy {

    private static final int OGG_PAGE_HEADER_SIZE = 27;
    private static final int VORBIS_PACKET_HEADER_SIZE = 7;
    private static final int MAX_PAGES = 10;
    private static final int MAX_VENDOR_LENGTH = 1024 * 1024;
    private static final int MAX_COMMENT_COUNT = 10000;

    // FLAC metadata block constants
    private static final int FLAC_SIGNATURE_LENGTH = 4;
    private static final int FLAC_BLOCK_HEADER_SIZE = 4;
    private static final int FLAC_LAST_BLOCK_FLAG = 0x80;
    private static final int FLAC_BLOCK_TYPE_MASK = 0x7F;
    private static final int FLAC_VORBIS_COMMENT_BLOCK_TYPE = 4;

    // OGG page constants
    private static final int OGG_SIGNATURE_LENGTH = 4;
    private static final int OGG_SEGMENT_COUNT_OFFSET = 26;
    private static final int VORBIS_IDENTIFICATION_PACKET_TYPE = 1;
    private static final int VORBIS_COMMENT_PACKET_TYPE = 3;
    private static final int VORBIS_SIGNATURE_LENGTH = 6;

    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.VORBIS_COMMENT);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (startBuffer.length < FLAC_SIGNATURE_LENGTH) return false;
        String signature = new String(startBuffer, 0, FLAC_SIGNATURE_LENGTH, StandardCharsets.US_ASCII);
        return signature.equals("OggS") || signature.equals("fLaC");
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        if (startBuffer.length < FLAC_SIGNATURE_LENGTH) return tags;

        String signature = new String(startBuffer, 0, FLAC_SIGNATURE_LENGTH, StandardCharsets.US_ASCII);
        if (signature.equals("OggS")) {
            CommentPacketInfo packetInfo = findCommentPacketDirect(file);
            if (packetInfo != null && validateCommentPacket(file, packetInfo.offset)) {
                tags.add(new TagInfo(TagFormat.VORBIS_COMMENT, packetInfo.offset, packetInfo.pageSize));
                LOG.debug("Found Vorbis Comment at offset: {}, size: {} bytes", packetInfo.offset, packetInfo.pageSize);
            }

            LOG.debug("Direct jump failed or seeking more packets, using sequential search in {}", filePath);
            tags.addAll(fallbackSequentialSearch(file));
        } else if (signature.equals("fLaC")) {
            long position = FLAC_SIGNATURE_LENGTH;
            while (position < file.length()) {
                file.seek(position);
                byte[] blockHeader = new byte[FLAC_BLOCK_HEADER_SIZE];
                file.read(blockHeader);
                boolean isLast = (blockHeader[0] & FLAC_LAST_BLOCK_FLAG) != 0;
                int blockType = blockHeader[0] & FLAC_BLOCK_TYPE_MASK;
                int blockLength = ((blockHeader[1] & 0xFF) << 16) | ((blockHeader[2] & 0xFF) << 8) | (blockHeader[3] & 0xFF);
                if (blockType == FLAC_VORBIS_COMMENT_BLOCK_TYPE) {
                    tags.add(new TagInfo(TagFormat.VORBIS_COMMENT, position, blockLength + FLAC_BLOCK_HEADER_SIZE));
                    LOG.debug("Found FLAC Vorbis Comment at offset: {}, size: {} bytes", position, blockLength + FLAC_BLOCK_HEADER_SIZE);
                }
                position += FLAC_BLOCK_HEADER_SIZE + blockLength;
                if (isLast) break;
            }
        }
        return tags;
    }

    private CommentPacketInfo findCommentPacketDirect(RandomAccessFile file) throws IOException {
        file.seek(0);
        long position = 0;

        byte[] buffer = new byte[OGG_PAGE_HEADER_SIZE];
        if (file.read(buffer) != OGG_PAGE_HEADER_SIZE || !new String(buffer, 0, OGG_SIGNATURE_LENGTH, StandardCharsets.US_ASCII).equals("OggS")) {
            LOG.debug("Invalid OGG header at position 0");
            return null;
        }

        int segmentCount = buffer[OGG_SEGMENT_COUNT_OFFSET] & 0xFF;
        byte[] segments = new byte[segmentCount];
        file.read(segments);

        long pageSize = OGG_PAGE_HEADER_SIZE + segmentCount;
        for (byte segment : segments) pageSize += (segment & 0xFF);

        file.seek(position + OGG_PAGE_HEADER_SIZE + segmentCount);
        byte[] packetHeader = new byte[VORBIS_PACKET_HEADER_SIZE];
        if (file.read(packetHeader) != VORBIS_PACKET_HEADER_SIZE || packetHeader[0] != VORBIS_IDENTIFICATION_PACKET_TYPE || !new String(packetHeader, 1, VORBIS_SIGNATURE_LENGTH, StandardCharsets.US_ASCII).equals("vorbis")) {
            LOG.debug("Invalid Identification packet at offset: {}", position + OGG_PAGE_HEADER_SIZE + segmentCount);
            return null;
        }

        position += pageSize;
        file.seek(position);
        if (file.read(buffer) != OGG_PAGE_HEADER_SIZE || !new String(buffer, 0, OGG_SIGNATURE_LENGTH, StandardCharsets.US_ASCII).equals("OggS")) {
            LOG.debug("Invalid OGG page at offset: {}", position);
            return null;
        }

        segmentCount = buffer[OGG_SEGMENT_COUNT_OFFSET] & 0xFF;
        segments = new byte[segmentCount];
        file.read(segments);

        long commentPageSize = OGG_PAGE_HEADER_SIZE + segmentCount;
        for (byte segment : segments) commentPageSize += (segment & 0xFF);

        long commentOffset = position + OGG_PAGE_HEADER_SIZE + segmentCount;
        return new CommentPacketInfo(commentOffset, commentPageSize);
    }

    private List<TagInfo> fallbackSequentialSearch(RandomAccessFile file) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        long position = 0;
        int pageCount = 0;

        while (position + OGG_PAGE_HEADER_SIZE < file.length() && pageCount < MAX_PAGES) {
            file.seek(position);
            byte[] buffer = new byte[OGG_PAGE_HEADER_SIZE];
            if (file.read(buffer) != OGG_PAGE_HEADER_SIZE || !new String(buffer, 0, OGG_SIGNATURE_LENGTH, StandardCharsets.US_ASCII).equals("OggS")) break;

            int segmentCount = buffer[OGG_SEGMENT_COUNT_OFFSET] & 0xFF;
            byte[] segments = new byte[segmentCount];
            file.read(segments);

            long pageSize = OGG_PAGE_HEADER_SIZE + segmentCount;
            for (byte segment : segments) pageSize += (segment & 0xFF);

            long commentOffset = position + OGG_PAGE_HEADER_SIZE + segmentCount;
            if (validateCommentPacket(file, commentOffset)) {
                tags.add(new TagInfo(TagFormat.VORBIS_COMMENT, commentOffset, pageSize));
                LOG.debug("Found Vorbis Comment via sequential search at offset: {}", commentOffset);
            }

            position += pageSize;
            pageCount++;
        }
        return tags;
    }

    private boolean validateCommentPacket(RandomAccessFile file, long offset) throws IOException {
        file.seek(offset);
        byte[] packetType = new byte[1];
        if (file.read(packetType) != 1 || packetType[0] != VORBIS_COMMENT_PACKET_TYPE) return false;

        byte[] lengthBytes = new byte[4];
        if (file.read(lengthBytes) != 4) return false;
        int vendorLength = ((lengthBytes[0] & 0xFF)) |
                ((lengthBytes[1] & 0xFF) << 8) |
                ((lengthBytes[2] & 0xFF) << 16) |
                ((lengthBytes[3] & 0xFF) << 24);

        if (vendorLength < 0 || vendorLength > MAX_VENDOR_LENGTH) {
            LOG.debug("Invalid vendor string length: {} at offset: {}", vendorLength, offset);
            return false;
        }

        file.skipBytes(vendorLength);

        if (file.read(lengthBytes) != 4) return false;
        int commentCount = ((lengthBytes[0] & 0xFF)) |
                ((lengthBytes[1] & 0xFF) << 8) |
                ((lengthBytes[2] & 0xFF) << 16) |
                ((lengthBytes[3] & 0xFF) << 24);

        if (commentCount < 0 || commentCount > MAX_COMMENT_COUNT) {
            LOG.debug("Invalid comment count: {} at offset: {}", commentCount, offset);
            return false;
        }

        return true;
    }

    private static class CommentPacketInfo {
        final long offset;
        final long pageSize;

        CommentPacketInfo(long offset, long pageSize) {
            this.offset = offset;
            this.pageSize = pageSize;
        }
    }

}