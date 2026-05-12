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

    // WavPack block/subblock thresholds
    private static final int MAX_BLOCKS = 10000;
    private static final int EARLY_EXIT_BLOCK_COUNT = 100;
    private static final long EARLY_EXIT_FILE_SIZE_THRESHOLD = 100L * 1024 * 1024;
    private static final long MAX_BLOCK_SIZE = 10L * 1024 * 1024;
    private static final int SKIP_HEADER_BYTES = 22;

    // Subblock flag masks
    private static final int SUBBLOCK_LARGE_FLAG = 0x80;
    private static final int SUBBLOCK_ID_MASK = 0x7F;
    private static final long UNSIGNED_INT_MASK = 0xFFFFFFFFL;

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

            while (currentPos + WAVPACK_HEADER_SIZE < fileLength && blockCount < MAX_BLOCKS) {
                file.seek(currentPos);

                WavPackBlockHeader header = parseBlockHeader(file);

                if (header == null) {
                    break;
                }

                List<TagInfo> blockTags = searchMetadataSubBlocks(file, currentPos + WAVPACK_HEADER_SIZE,
                        header.blockSize - WAVPACK_HEADER_SIZE);
                tags.addAll(blockTags);

                currentPos += header.blockSize;
                blockCount++;

                if (blockCount >= EARLY_EXIT_BLOCK_COUNT && fileLength > EARLY_EXIT_FILE_SIZE_THRESHOLD) {
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

    private WavPackBlockHeader parseBlockHeader(RandomAccessFile file) throws IOException {
        byte[] magic = new byte[4];
        file.read(magic);

        if (!Arrays.equals(magic, WAVPACK_SIGNATURE)) {
            return null;
        }

        long blockSize = readLittleEndianInt(file) & UNSIGNED_INT_MASK;
        int version = readLittleEndianShort(file);

        file.skipBytes(SKIP_HEADER_BYTES);

        if (blockSize < WAVPACK_HEADER_SIZE || blockSize > MAX_BLOCK_SIZE) {
            LOG.warn("Invalid WavPack block size: {}", blockSize);
            return null;
        }

        return new WavPackBlockHeader(blockSize);
    }

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
                    long subBlockStart = currentPos - 0 + 0;
                    long totalSubBlockSize = subBlockSize + headerSize;
                    subBlockStart = currentPos;

                    tags.add(new TagInfo(TagFormat.WAVPACK_NATIVE, subBlockStart, totalSubBlockSize));

                    String subBlockName = METADATA_SUBBLOCKS.get(cleanId);
                    LOG.debug("Found WavPack metadata sub-block: {} at offset: {}, size: {} bytes",
                            subBlockName, subBlockStart, totalSubBlockSize);
                }

                currentPos += headerSize + subBlockSize;

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

    private int readLittleEndianShort(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[2];
        file.read(bytes);
        return ((bytes[0] & 0xFF)) | ((bytes[1] & 0xFF) << 8);
    }

    private int readLittleEndianInt(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[4];
        file.read(bytes);
        return ((bytes[0] & 0xFF)) |
                ((bytes[1] & 0xFF) << 8) |
                ((bytes[2] & 0xFF) << 16) |
                ((bytes[3] & 0xFF) << 24);
    }

    private static class WavPackBlockHeader {
        final long blockSize;

        WavPackBlockHeader(long blockSize) {
            this.blockSize = blockSize;
        }
    }
}