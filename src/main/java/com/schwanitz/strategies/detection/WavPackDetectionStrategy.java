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
 * - Block Size: 32-bit LE (4 bytes, includes header)
 * - Version: 16-bit LE (2 bytes)
 * - Track Length: 24-bit LE (3 bytes)
 * - Block Index: 32-bit LE (4 bytes)
 * - Total Samples: 32-bit LE (4 bytes)
 * - Block Samples: 32-bit LE (4 bytes)
 * - Flags: 32-bit LE (4 bytes)
 * - CRC: 32-bit LE (4 bytes)
 * <p>
 * The Flags field contains a bit indicating whether this block contains
 * metadata subblocks (INITIAL_BLOCK flag bit 2). Only blocks with
 * metadata need to be scanned for subblocks.
 * <p>
 * Metadata is stored in subblocks with IDs like:
 * - 0x21: RIFF Header (WAV metadata)
 * - 0x22: RIFF Trailer (tags)
 * - 0x26: MD5 Checksum
 * <p>
 * Detection strategy: walk top-level blocks by reading only headers
 * and skipping to the next block. Only scan subblocks in blocks that
 * contain metadata subblocks (indicated by the INITIAL_BLOCK or
 * blocks where subblock count > 0).
 */
public class WavPackDetectionStrategy extends TagDetectionStrategy {

    private static final byte[] WAVPACK_SIGNATURE = {'w', 'v', 'p', 'k'};

    // WavPack block header structure
    private static final int HEADER_SIZE = 32;
    private static final int SIGNATURE_OFFSET = 0;
    private static final int BLOCK_SIZE_OFFSET = 4;
    private static final int VERSION_OFFSET = 8;
    private static final int BLOCK_INDEX_OFFSET = 12;
    private static final int TOTAL_SAMPLES_OFFSET = 16;
    private static final int BLOCK_SAMPLES_OFFSET = 20;
    private static final int FLAGS_OFFSET = 24;
    private static final int CRC_OFFSET = 28;

    // WavPack flags
    private static final int FLAG_INITIAL_BLOCK = 0x02;
    private static final int FLAG_FINAL_BLOCK = 0x04;

    // Subblock flag masks
    private static final int SUBBLOCK_LARGE_FLAG = 0x80;
    private static final int SUBBLOCK_ID_MASK = 0x7F;
    private static final long UNSIGNED_INT_MASK = 0xFFFFFFFFL;

    // Safety limits
    private static final long MAX_REASONABLE_BLOCK_SIZE = 10L * 1024 * 1024;
    private static final long MAX_REASONABLE_FILE_SCAN = 500L * 1024 * 1024;

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

        LOG.debug("Detecting WavPack native metadata in file: {}", filePath);

        try {
            long currentPos = 0;
            long fileLength = file.length();
            int blockCount = 0;

            while (currentPos + HEADER_SIZE < fileLength) {
                file.seek(currentPos);

                byte[] headerBytes = new byte[HEADER_SIZE];
                int bytesRead = file.read(headerBytes);
                if (bytesRead != HEADER_SIZE) {
                    break;
                }

                // Verify magic signature
                if (!Arrays.equals(headerBytes, SIGNATURE_OFFSET, 4, WAVPACK_SIGNATURE, 0, 4)) {
                    LOG.debug("Lost WavPack signature at offset {}, stopping scan", currentPos);
                    break;
                }

                long blockSize = readLittleEndianInt(headerBytes, BLOCK_SIZE_OFFSET) & UNSIGNED_INT_MASK;

                if (blockSize < HEADER_SIZE || blockSize > MAX_REASONABLE_BLOCK_SIZE) {
                    LOG.debug("Invalid WavPack block size {} at offset {}, stopping scan", blockSize, currentPos);
                    break;
                }

                // Read flags to determine if this block has metadata subblocks
                int flags = readLittleEndianInt(headerBytes, FLAGS_OFFSET);

                // Metadata subblocks appear in initial blocks of each channel group
                // We check for metadata in blocks that are INITIAL (first channel of a multi-channel pair)
                // or in single-channel blocks where INITIAL and FINAL are both set
                if ((flags & FLAG_INITIAL_BLOCK) != 0) {
                    List<TagInfo> blockTags = searchMetadataSubBlocks(file, currentPos + HEADER_SIZE,
                            blockSize - HEADER_SIZE);
                    tags.addAll(blockTags);
                }

                // Jump directly to next block using the block size
                currentPos += blockSize;
                blockCount++;

                // Safety: stop if we scan beyond a reasonable file region
                if (currentPos > MAX_REASONABLE_FILE_SCAN) {
                    LOG.debug("WavPack scan reached safety limit at offset {}", currentPos);
                    break;
                }
            }

            LOG.debug("WavPack detection completed: found {} metadata sub-blocks in {} blocks",
                    tags.size(), blockCount);

        } catch (IOException e) {
            LOG.error("Error detecting WavPack metadata in {}: {}", filePath, e.getMessage());
            throw e;
        }

        return tags;
    }

    /**
     * Search subblocks for metadata within a single WavPack block.
     * Only called for blocks that are likely to contain metadata (initial blocks).
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
                int headerSize;

                if ((subBlockId & SUBBLOCK_LARGE_FLAG) != 0) {
                    byte[] sizeBytes = new byte[3];
                    int bytesRead = file.read(sizeBytes);
                    if (bytesRead != 3) break;

                    subBlockSize = (sizeBytes[0] & 0xFF) |
                            ((sizeBytes[1] & 0xFF) << 8) |
                            ((sizeBytes[2] & 0xFF) << 16);
                    headerSize = 4;
                } else {
                    subBlockSize = file.read();
                    if (subBlockSize == -1) break;

                    subBlockSize = (subBlockSize << 1);
                    headerSize = 2;
                }

                if (subBlockSize < 0 || subBlockSize > blockEnd - currentPos) {
                    break;
                }

                int cleanId = subBlockId & SUBBLOCK_ID_MASK;
                if (METADATA_SUBBLOCKS.containsKey(cleanId)) {
                    long subBlockStart = currentPos;
                    long totalSubBlockSize = subBlockSize + headerSize;

                    tags.add(new TagInfo(TagFormat.WAVPACK_NATIVE, subBlockStart, totalSubBlockSize));

                    String subBlockName = METADATA_SUBBLOCKS.get(cleanId);
                    LOG.debug("Found WavPack metadata sub-block: {} at offset: {}, size: {} bytes",
                            subBlockName, subBlockStart, totalSubBlockSize);
                }

                currentPos += headerSize + subBlockSize;

                // Word alignment
                if (currentPos % 2 != 0) {
                    currentPos++;
                }

            } catch (IOException e) {
                LOG.debug("Error parsing WavPack sub-block at position {}: {}", currentPos, e.getMessage());
                break;
            }
        }

        return tags;
    }

    private int readLittleEndianInt(byte[] data, int offset) {
        return (data[offset] & 0xFF) |
                ((data[offset + 1] & 0xFF) << 8) |
                ((data[offset + 2] & 0xFF) << 16) |
                ((data[offset + 3] & 0xFF) << 24);
    }
}