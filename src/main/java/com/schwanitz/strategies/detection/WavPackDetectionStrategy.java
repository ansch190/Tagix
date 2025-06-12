package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Detection Strategy for WavPack (.wv) native metadata
 * <p>
 * WavPack uses a block-based format with 32-byte headers.
 * Block Header Structure:
 * - Magic: "wvpk" (4 bytes)
 * - Block Size: 32-bit LE (4 bytes)
 * - Version: 16-bit LE (2 bytes)
 * - Track Length: 24-bit LE (3 bytes)
 * - Block Index: 32-bit LE (4 bytes)
 * - Total Samples: 32-bit LE (4 bytes)
 * - Block Samples: 32-bit LE (4 bytes)
 * - Flags: 32-bit LE (4 bytes)
 * - CRC: 32-bit LE (4 bytes)
 * <p>
 * Metadata is stored in subblocks with IDs like:
 * - 0x21: RIFF Header (WAV metadata)
 * - 0x22: RIFF Trailer (tags)
 * - 0x26: MD5 Checksum
 */
public class WavPackDetectionStrategy extends TagDetectionStrategy {

    private static final byte[] WAVPACK_SIGNATURE = {'w', 'v', 'p', 'k'};
    private static final int WAVPACK_HEADER_SIZE = 32;

    // Important subblock IDs for metadata
    private static final Map<Integer, String> METADATA_SUBBLOCKS = new HashMap<>();

    static {
        METADATA_SUBBLOCKS.put(0x21, "RIFF Header");
        METADATA_SUBBLOCKS.put(0x22, "RIFF Trailer");
        METADATA_SUBBLOCKS.put(0x23, "Alternative Header");
        METADATA_SUBBLOCKS.put(0x24, "Alternative Trailer");
        METADATA_SUBBLOCKS.put(0x25, "Configuration Block");
        METADATA_SUBBLOCKS.put(0x26, "MD5 Checksum");
    }

    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.WAVPACK_NATIVE);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (startBuffer.length < 4) {
            return false;
        }
        return Arrays.equals(Arrays.copyOfRange(startBuffer, 0, 4), WAVPACK_SIGNATURE);
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath,
                                    byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        if (!canDetect(startBuffer, endBuffer)) {
            return tags;
        }

        Log.debug("Detecting WavPack native metadata in file: {}", filePath);

        try {
            long currentPos = 0;
            long fileLength = file.length();
            int blockCount = 0;
            final int MAX_BLOCKS = 10000;

            while (currentPos + WAVPACK_HEADER_SIZE < fileLength && blockCount < MAX_BLOCKS) {
                file.seek(currentPos);

                WavPackBlockHeader header = parseBlockHeader(file);

                if (header == null) {
                    break;
                }

                // Search metadata subblocks in this block
                List<TagInfo> blockTags = searchMetadataSubBlocks(file, currentPos + WAVPACK_HEADER_SIZE,
                        header.blockSize - WAVPACK_HEADER_SIZE);
                tags.addAll(blockTags);

                currentPos += header.blockSize;
                blockCount++;

                // Limit analysis for very large files
                if (blockCount >= 100 && fileLength > 100 * 1024 * 1024) {
                    break;
                }
            }

            Log.debug("WavPack detection completed: found {} metadata sub-blocks in {} blocks",
                    tags.size(), blockCount);

        } catch (IOException e) {
            Log.error("Error detecting WavPack metadata in {}: {}", filePath, e.getMessage());
            throw e;
        }

        return tags;
    }

    /**
     * Parse WavPack block header
     */
    private WavPackBlockHeader parseBlockHeader(RandomAccessFile file) throws IOException {
        byte[] magic = new byte[4];
        file.read(magic);

        if (!Arrays.equals(magic, WAVPACK_SIGNATURE)) {
            return null;
        }

        long blockSize = readLittleEndianInt(file) & 0xFFFFFFFFL;
        int version = readLittleEndianShort(file);

        // Skip other header fields
        file.skipBytes(22);

        if (blockSize < WAVPACK_HEADER_SIZE || blockSize > 10 * 1024 * 1024) {
            Log.warn("Invalid WavPack block size: {}", blockSize);
            return null;
        }

        return new WavPackBlockHeader(blockSize);
    }

    /**
     * Search subblocks for metadata
     */
    private List<TagInfo> searchMetadataSubBlocks(RandomAccessFile file, long blockDataStart,
                                                  long blockDataSize) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        long currentPos = blockDataStart;
        long blockEnd = blockDataStart + blockDataSize;

        while (currentPos + 2 < blockEnd) {
            file.seek(currentPos);

            try {
                int subBlockId = file.read();
                if (subBlockId == -1) break;

                int subBlockSize;

                if ((subBlockId & 0x80) != 0) {
                    // Large subblock: 3-byte size
                    byte[] sizeBytes = new byte[3];
                    int bytesRead = file.read(sizeBytes);
                    if (bytesRead != 3) break;

                    subBlockSize = (sizeBytes[0] & 0xFF) |
                            ((sizeBytes[1] & 0xFF) << 8) |
                            ((sizeBytes[2] & 0xFF) << 16);
                    currentPos += 4;
                } else {
                    // Small subblock: 1-byte size
                    subBlockSize = file.read();
                    if (subBlockSize == -1) break;

                    subBlockSize = (subBlockSize << 1);
                    currentPos += 2;
                }

                if (subBlockSize < 0 || subBlockSize > blockEnd - currentPos) {
                    break;
                }

                // Check for metadata-relevant sub-blocks
                int cleanId = subBlockId & 0x7F;
                if (METADATA_SUBBLOCKS.containsKey(cleanId)) {
                    long subBlockStart = currentPos - (((subBlockId & 0x80) != 0) ? 4 : 2);
                    long totalSubBlockSize = subBlockSize + (((subBlockId & 0x80) != 0) ? 4 : 2);

                    tags.add(new TagInfo(TagFormat.WAVPACK_NATIVE, subBlockStart, totalSubBlockSize));

                    String subBlockName = METADATA_SUBBLOCKS.get(cleanId);
                    Log.debug("Found WavPack metadata sub-block: {} at offset: {}, size: {} bytes",
                            subBlockName, subBlockStart, totalSubBlockSize);
                }

                currentPos += subBlockSize;

                // Word alignment
                if (currentPos % 2 != 0) {
                    currentPos++;
                }

            } catch (IOException e) {
                Log.debug("Error parsing WavPack sub-block at position {}: {}", currentPos, e.getMessage());
                break;
            }
        }

        return tags;
    }

    /**
     * Read 16-bit little-endian short
     */
    private int readLittleEndianShort(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[2];
        file.read(bytes);
        return ((bytes[0] & 0xFF)) | ((bytes[1] & 0xFF) << 8);
    }

    /**
     * Read 32-bit little-endian integer
     */
    private int readLittleEndianInt(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[4];
        file.read(bytes);
        return ((bytes[0] & 0xFF)) |
                ((bytes[1] & 0xFF) << 8) |
                ((bytes[2] & 0xFF) << 16) |
                ((bytes[3] & 0xFF) << 24);
    }

    /**
     * WavPack Block Header data class
     */
    private static class WavPackBlockHeader {
        final long blockSize;

        WavPackBlockHeader(long blockSize) {
            this.blockSize = blockSize;
        }
    }
}