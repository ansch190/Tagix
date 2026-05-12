package com.schwanitz.strategies.detection;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Erkennungsstrategie für Vorbis Comments in OGG- und FLAC-Dateien.
 * <p>
 * Vorbis Comments werden verwendet in:
 * <ul>
 *   <li>OGG-Dateien: Eingebettet in Ogg-Seiten mit Pakettyp 0x03</li>
 *   <li>FLAC-Dateien: Als Metadatenblock-Typ 4</li>
 * </ul>
 * <p>
 * Der Inhalt ist standardisiert:
 * <ul>
 *   <li>Vendor-String (Länge + UTF-8-Text)</li>
 *   <li>Kommentaranzahl (32-Bit Little-Endian)</li>
 *   <li>Kommentare (Länge + UTF-8 Schlüssel=Wert-Paare)</li>
 * </ul>
 * <p>
 * Verwendet einen hybriden Ansatz für OGG: direkter Sprung zum Kommentar-Paket
 * mit Fallback auf sequenzielle Suche.
 */
public class VorbisCommentDetectionStrategy extends TagDetectionStrategy {

    private static final int OGG_PAGE_HEADER_SIZE = 27;
    private static final int VORBIS_PACKET_HEADER_SIZE = 7;
    private static final int MAX_PAGES = 10;
    private static final int MAX_VENDOR_LENGTH = 1024 * 1024;
    private static final int MAX_COMMENT_COUNT = 10000;

    private static final int FLAC_SIGNATURE_LENGTH = 4;
    private static final int FLAC_BLOCK_HEADER_SIZE = 4;
    private static final int FLAC_LAST_BLOCK_FLAG = 0x80;
    private static final int FLAC_BLOCK_TYPE_MASK = 0x7F;
    private static final int FLAC_VORBIS_COMMENT_BLOCK_TYPE = 4;

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
    public List<TagInfo> detectTags(SeekableDataSource source, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        if (startBuffer.length < FLAC_SIGNATURE_LENGTH) return tags;

        String signature = new String(startBuffer, 0, FLAC_SIGNATURE_LENGTH, StandardCharsets.US_ASCII);
        if (signature.equals("OggS")) {
            CommentPacketInfo packetInfo = findCommentPacketDirect(source);
            if (packetInfo != null && validateCommentPacket(source, packetInfo.offset)) {
                tags.add(new TagInfo(TagFormat.VORBIS_COMMENT, packetInfo.offset, packetInfo.pageSize));
                LOG.debug("Found Vorbis Comment at offset: {}, size: {} bytes", packetInfo.offset, packetInfo.pageSize);
            }

            LOG.debug("Direct jump failed or seeking more packets, using sequential search in {}", source.name());
            tags.addAll(fallbackSequentialSearch(source));
        } else if (signature.equals("fLaC")) {
            long position = FLAC_SIGNATURE_LENGTH;
            long sourceLength = source.length();
            while (position < sourceLength) {
                byte[] blockHeader = new byte[FLAC_BLOCK_HEADER_SIZE];
                int bytesRead = source.read(position, blockHeader, 0, FLAC_BLOCK_HEADER_SIZE);
                if (bytesRead != FLAC_BLOCK_HEADER_SIZE) break;
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

    private CommentPacketInfo findCommentPacketDirect(SeekableDataSource source) throws IOException {
        long position = 0;

        byte[] buffer = new byte[OGG_PAGE_HEADER_SIZE];
        if (source.read(position, buffer, 0, OGG_PAGE_HEADER_SIZE) != OGG_PAGE_HEADER_SIZE ||
                !new String(buffer, 0, OGG_SIGNATURE_LENGTH, StandardCharsets.US_ASCII).equals("OggS")) {
            LOG.debug("Invalid OGG header at position 0");
            return null;
        }

        int segmentCount = buffer[OGG_SEGMENT_COUNT_OFFSET] & 0xFF;
        byte[] segments = new byte[segmentCount];
        if (source.read(position + OGG_PAGE_HEADER_SIZE, segments, 0, segmentCount) != segmentCount) {
            return null;
        }

        long pageSize = OGG_PAGE_HEADER_SIZE + segmentCount;
        for (byte segment : segments) pageSize += (segment & 0xFF);

        long packetHeaderOffset = position + OGG_PAGE_HEADER_SIZE + segmentCount;
        byte[] packetHeader = new byte[VORBIS_PACKET_HEADER_SIZE];
        if (source.read(packetHeaderOffset, packetHeader, 0, VORBIS_PACKET_HEADER_SIZE) != VORBIS_PACKET_HEADER_SIZE ||
                packetHeader[0] != VORBIS_IDENTIFICATION_PACKET_TYPE ||
                !new String(packetHeader, 1, VORBIS_SIGNATURE_LENGTH, StandardCharsets.US_ASCII).equals("vorbis")) {
            LOG.debug("Invalid Identification packet at offset: {}", packetHeaderOffset);
            return null;
        }

        position += pageSize;
        if (source.read(position, buffer, 0, OGG_PAGE_HEADER_SIZE) != OGG_PAGE_HEADER_SIZE ||
                !new String(buffer, 0, OGG_SIGNATURE_LENGTH, StandardCharsets.US_ASCII).equals("OggS")) {
            LOG.debug("Invalid OGG page at offset: {}", position);
            return null;
        }

        segmentCount = buffer[OGG_SEGMENT_COUNT_OFFSET] & 0xFF;
        segments = new byte[segmentCount];
        if (source.read(position + OGG_PAGE_HEADER_SIZE, segments, 0, segmentCount) != segmentCount) {
            return null;
        }

        long commentPageSize = OGG_PAGE_HEADER_SIZE + segmentCount;
        for (byte segment : segments) commentPageSize += (segment & 0xFF);

        long commentOffset = position + OGG_PAGE_HEADER_SIZE + segmentCount;
        return new CommentPacketInfo(commentOffset, commentPageSize);
    }

    private List<TagInfo> fallbackSequentialSearch(SeekableDataSource source) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        long position = 0;
        int pageCount = 0;
        long sourceLength = source.length();

        byte[] buffer = new byte[OGG_PAGE_HEADER_SIZE];
        while (position + OGG_PAGE_HEADER_SIZE < sourceLength && pageCount < MAX_PAGES) {
            if (source.read(position, buffer, 0, OGG_PAGE_HEADER_SIZE) != OGG_PAGE_HEADER_SIZE) break;
            if (!new String(buffer, 0, OGG_SIGNATURE_LENGTH, StandardCharsets.US_ASCII).equals("OggS")) break;

            int segmentCount = buffer[OGG_SEGMENT_COUNT_OFFSET] & 0xFF;
            byte[] segments = new byte[segmentCount];
            if (source.read(position + OGG_PAGE_HEADER_SIZE, segments, 0, segmentCount) != segmentCount) break;

            long pageSize = OGG_PAGE_HEADER_SIZE + segmentCount;
            for (byte segment : segments) pageSize += (segment & 0xFF);

            long commentOffset = position + OGG_PAGE_HEADER_SIZE + segmentCount;
            if (validateCommentPacket(source, commentOffset)) {
                tags.add(new TagInfo(TagFormat.VORBIS_COMMENT, commentOffset, pageSize));
                LOG.debug("Found Vorbis Comment via sequential search at offset: {}", commentOffset);
            }

            position += pageSize;
            pageCount++;
        }
        return tags;
    }

    private boolean validateCommentPacket(SeekableDataSource source, long offset) throws IOException {
        byte[] packetType = new byte[1];
        if (source.read(offset, packetType, 0, 1) != 1 || packetType[0] != VORBIS_COMMENT_PACKET_TYPE) return false;

        byte[] lengthBytes = new byte[4];
        if (source.read(offset + 1, lengthBytes, 0, 4) != 4) return false;
        int vendorLength = ((lengthBytes[0] & 0xFF)) |
                ((lengthBytes[1] & 0xFF) << 8) |
                ((lengthBytes[2] & 0xFF) << 16) |
                ((lengthBytes[3] & 0xFF) << 24);

        if (vendorLength < 0 || vendorLength > MAX_VENDOR_LENGTH) {
            LOG.debug("Invalid vendor string length: {} at offset: {}", vendorLength, offset);
            return false;
        }

        long currentPos = offset + 1 + 4 + vendorLength;

        if (source.read(currentPos, lengthBytes, 0, 4) != 4) return false;
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