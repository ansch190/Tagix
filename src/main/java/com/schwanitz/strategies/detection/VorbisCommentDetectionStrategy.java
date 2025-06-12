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
 * <p>
 * Uses hybrid approach for OGG: direct jump to a comment packet with fallback to sequential search
 */
public class VorbisCommentDetectionStrategy extends TagDetectionStrategy {

    private static final int OGG_PAGE_HEADER_SIZE = 27;
    private static final int VORBIS_PACKET_HEADER_SIZE = 7;
    private static final int MAX_PAGES = 10;
    private static final int MAX_VENDOR_LENGTH = 1024 * 1024; // 1MB
    private static final int MAX_COMMENT_COUNT = 10000;

    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.VORBIS_COMMENT);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (startBuffer.length < 4) return false;
        String signature = new String(startBuffer, 0, 4);
        return signature.equals("OggS") || signature.equals("fLaC");
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        if (startBuffer.length < 4) return tags;

        String signature = new String(startBuffer, 0, 4);
        if (signature.equals("OggS")) {
            // Try direct jump to a comment packet
            CommentPacketInfo packetInfo = findCommentPacketDirect(file);
            if (packetInfo != null && validateCommentPacket(file, packetInfo.offset)) {
                tags.add(new TagInfo(TagFormat.VORBIS_COMMENT, packetInfo.offset, packetInfo.pageSize));
                Log.debug("Found Vorbis Comment at offset: {}, size: {} bytes", packetInfo.offset, packetInfo.pageSize);
            }

            // Search for additional comment packets and use fallback if needed
            Log.debug("Direct jump failed or seeking more packets, using sequential search in {}", filePath);
            tags.addAll(fallbackSequentialSearch(file));
        } else if (signature.equals("fLaC")) {
            // Parse FLAC Vorbis Comment block
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
                    Log.debug("Found FLAC Vorbis Comment at offset: {}, size: {} bytes", position, blockLength + 4);
                }
                position += 4 + blockLength;
                if (isLast) break;
            }
        }
        return tags;
    }

    /**
     * Locate Vorbis Comment packet (Type 0x03) by parsing the first OGG page
     */
    private CommentPacketInfo findCommentPacketDirect(RandomAccessFile file) throws IOException {
        file.seek(0);
        long position = 0;

        // Read first page header (Identification packet, Type 0x01)
        byte[] buffer = new byte[OGG_PAGE_HEADER_SIZE];
        if (file.read(buffer) != OGG_PAGE_HEADER_SIZE || !new String(buffer, 0, 4).equals("OggS")) {
            Log.debug("Invalid OGG header at position 0");
            return null;
        }

        int segmentCount = buffer[26] & 0xFF;
        byte[] segments = new byte[segmentCount];
        file.read(segments);

        // Calculate first page size
        long pageSize = OGG_PAGE_HEADER_SIZE + segmentCount;
        for (byte segment : segments) pageSize += (segment & 0xFF);

        // Verify an Identification packet
        file.seek(position + OGG_PAGE_HEADER_SIZE + segmentCount);
        byte[] packetHeader = new byte[VORBIS_PACKET_HEADER_SIZE];
        if (file.read(packetHeader) != VORBIS_PACKET_HEADER_SIZE || packetHeader[0] != 1 || !new String(packetHeader, 1, 6).equals("vorbis")) {
            Log.debug("Invalid Identification packet at offset: {}", position + OGG_PAGE_HEADER_SIZE + segmentCount);
            return null;
        }

        // Jump to the next page (Comment packet, Type 0x03)
        position += pageSize;
        file.seek(position);
        if (file.read(buffer) != OGG_PAGE_HEADER_SIZE || !new String(buffer, 0, 4).equals("OggS")) {
            Log.debug("Invalid OGG page at offset: {}", position);
            return null;
        }

        segmentCount = buffer[26] & 0xFF;
        segments = new byte[segmentCount];
        file.read(segments);

        // Calculate second page size
        long commentPageSize = OGG_PAGE_HEADER_SIZE + segmentCount;
        for (byte segment : segments) commentPageSize += (segment & 0xFF);

        long commentOffset = position + OGG_PAGE_HEADER_SIZE + segmentCount;
        return new CommentPacketInfo(commentOffset, commentPageSize);
    }

    /**
     * Fallback sequential search for Vorbis Comment packets
     */
    private List<TagInfo> fallbackSequentialSearch(RandomAccessFile file) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        long position = 0;
        int pageCount = 0;

        while (position + OGG_PAGE_HEADER_SIZE < file.length() && pageCount < MAX_PAGES) {
            file.seek(position);
            byte[] buffer = new byte[OGG_PAGE_HEADER_SIZE];
            if (file.read(buffer) != OGG_PAGE_HEADER_SIZE || !new String(buffer, 0, 4).equals("OggS")) break;

            int segmentCount = buffer[26] & 0xFF;
            byte[] segments = new byte[segmentCount];
            file.read(segments);

            long pageSize = OGG_PAGE_HEADER_SIZE + segmentCount;
            for (byte segment : segments) pageSize += (segment & 0xFF);

            long commentOffset = position + OGG_PAGE_HEADER_SIZE + segmentCount;
            if (validateCommentPacket(file, commentOffset)) {
                tags.add(new TagInfo(TagFormat.VORBIS_COMMENT, commentOffset, pageSize));
                Log.debug("Found Vorbis Comment via sequential search at offset: {}", commentOffset);
            }

            position += pageSize;
            pageCount++;
        }
        return tags;
    }

    /**
     * Validate Vorbis Comment packet data
     */
    private boolean validateCommentPacket(RandomAccessFile file, long offset) throws IOException {
        file.seek(offset);
        byte[] packetType = new byte[1];
        if (file.read(packetType) != 1 || packetType[0] != 3) return false;

        // Read vendor string length (32-bit little-endian)
        byte[] lengthBytes = new byte[4];
        if (file.read(lengthBytes) != 4) return false;
        int vendorLength = ((lengthBytes[0] & 0xFF)) |
                ((lengthBytes[1] & 0xFF) << 8) |
                ((lengthBytes[2] & 0xFF) << 16) |
                ((lengthBytes[3] & 0xFF) << 24);

        if (vendorLength < 0 || vendorLength > MAX_VENDOR_LENGTH) {
            Log.debug("Invalid vendor string length: {} at offset: {}", vendorLength, offset);
            return false;
        }

        // Skip vendor string
        file.skipBytes(vendorLength);

        // Read comment count (32-bit little-endian)
        if (file.read(lengthBytes) != 4) return false;
        int commentCount = ((lengthBytes[0] & 0xFF)) |
                ((lengthBytes[1] & 0xFF) << 8) |
                ((lengthBytes[2] & 0xFF) << 16) |
                ((lengthBytes[3] & 0xFF) << 24);

        if (commentCount < 0 || commentCount > MAX_COMMENT_COUNT) {
            Log.debug("Invalid comment count: {} at offset: {}", commentCount, offset);
            return false;
        }

        return true;
    }

    /**
     * Data class for comment packet information
     */
    private static class CommentPacketInfo {
        final long offset;
        final long pageSize;

        CommentPacketInfo(long offset, long pageSize) {
            this.offset = offset;
            this.pageSize = pageSize;
        }
    }

}