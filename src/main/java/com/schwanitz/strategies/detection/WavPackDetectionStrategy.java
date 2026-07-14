package com.schwanitz.strategies.detection;

import static com.schwanitz.formats.wavpack.WavPackConstants.*;

import com.schwanitz.io.BinaryDataReader;
import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Erkennungsstrategie für WavPack-(.wv)-Native-Metadaten.
 * <p>
 * WavPack verwendet ein blockbasiertes Format mit 32-Byte-Headern.
 * Block-Header-Struktur:
 * <ul>
 *   <li>Kennzeichen: "wvpk" (4 Bytes)</li>
 *   <li>Blockgröße: 32-Bit LE (4 Bytes, inklusive Header)</li>
 *   <li>Version: 16-Bit LE (2 Bytes)</li>
 *   <li>Spurlänge: 24-Bit LE (3 Bytes)</li>
 *   <li>Blockindex: 32-Bit LE (4 Bytes)</li>
 *   <li>Gesamt Samples: 32-Bit LE (4 Bytes)</li>
 *   <li>Block-Samples: 32-Bit LE (4 Bytes)</li>
 *   <li>Flags: 32-Bit LE (4 Bytes)</li>
 *   <li>CRC: 32-Bit LE (4 Bytes)</li>
 * </ul>
 * <p>
 * Das Flags-Feld enthält ein Bit, das angibt, ob dieser Block Metadaten-Unterblöcke
 * enthält (INITIAL_BLOCK-Flag, Bit 2). Nur Blöcke mit Metadaten müssen nach
 * Unterblöcken durchsucht werden.
 * <p>
 * Metadaten werden in Unterblöcken mit folgenden IDs gespeichert:
 * <ul>
 *   <li>0x21: RIFF Header (WAV-Metadaten)</li>
 *   <li>0x22: RIFF Trailer (Tags)</li>
 *   <li>0x26: MD5-Prüfsumme</li>
 * </ul>
 * <p>
 * Erkennungsstrategie: Top-Level-Blöcke werden durch Lesen der Header durchlaufen,
 * wobei direkt zum nächsten Block gesprungen wird. Nur Blöcke mit dem INITIAL_BLOCK-Flag
 * werden nach Metadaten-Unterblöcken durchsucht.
 */
public class WavPackDetectionStrategy extends TagDetectionStrategy {

    private static final int HEADER_SIZE = WAVPACK_HEADER_SIZE;
    private static final int SIGNATURE_OFFSET = 0;
    private static final int BLOCK_SIZE_OFFSET = 4;
    private static final int FLAGS_OFFSET = 24;

    private static final int FLAG_INITIAL_BLOCK = 0x02;

    private static final long UNSIGNED_INT_MASK = 0xFFFFFFFFL;

    private static final long MAX_REASONABLE_BLOCK_SIZE = 10L * 1024 * 1024;
    private static final long MAX_REASONABLE_FILE_SCAN = 500L * 1024 * 1024;

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
    public List<TagInfo> detectTags(SeekableDataSource source, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        if (!canDetect(startBuffer, endBuffer)) {
            return tags;
        }

        LOG.debug("Detecting WavPack native metadata in source: {}", source.name());

        try {
            long currentPos = 0;
            long sourceLength = source.length();
            int blockCount = 0;

            while (currentPos + HEADER_SIZE < sourceLength) {
                byte[] headerBytes = new byte[HEADER_SIZE];
                int bytesRead = source.read(currentPos, headerBytes, 0, HEADER_SIZE);
                if (bytesRead != HEADER_SIZE) {
                    break;
                }

                if (!Arrays.equals(headerBytes, SIGNATURE_OFFSET, 4, WAVPACK_SIGNATURE, 0, 4)) {
                    LOG.debug("Lost WavPack signature at offset {}, stopping scan", currentPos);
                    break;
                }

                long blockSize = BinaryDataReader.readLittleEndianInt32(headerBytes, BLOCK_SIZE_OFFSET) & UNSIGNED_INT_MASK;

                if (blockSize < HEADER_SIZE || blockSize > MAX_REASONABLE_BLOCK_SIZE) {
                    LOG.debug("Invalid WavPack block size {} at offset {}, stopping scan", blockSize, currentPos);
                    break;
                }

                int flags = BinaryDataReader.readLittleEndianInt32(headerBytes, FLAGS_OFFSET);

                if ((flags & FLAG_INITIAL_BLOCK) != 0) {
                    List<TagInfo> blockTags = searchMetadataSubBlocks(source, currentPos + HEADER_SIZE,
                            blockSize - HEADER_SIZE);
                    tags.addAll(blockTags);
                }

                currentPos += blockSize;
                blockCount++;

                if (currentPos > MAX_REASONABLE_FILE_SCAN) {
                    LOG.debug("WavPack scan reached safety limit at offset {}", currentPos);
                    break;
                }
            }

            LOG.debug("WavPack detection completed: found {} metadata sub-blocks in {} blocks",
                    tags.size(), blockCount);

        } catch (IOException e) {
            LOG.error("Error detecting WavPack metadata in {}", source.name(), e);
            throw e;
        }

        return tags;
    }

    private List<TagInfo> searchMetadataSubBlocks(SeekableDataSource source, long blockDataStart,
                                                   long blockDataSize) {
        List<TagInfo> tags = new ArrayList<>();

        long currentPos = blockDataStart;
        long blockEnd = blockDataStart + blockDataSize;

        while (currentPos + 2 < blockEnd) {
            try {
                byte[] subBlockIdBuf = new byte[1];
                source.read(currentPos, subBlockIdBuf, 0, 1);
                int subBlockId = subBlockIdBuf[0] & 0xFF;
                if (subBlockIdBuf[0] == -1) break;

                int subBlockSize;
                int headerSize;

                if ((subBlockId & SUBBLOCK_LARGE_FLAG) != 0) {
                    byte[] sizeBytes = new byte[3];
                    int bytesRead = source.read(currentPos + 1, sizeBytes, 0, 3);
                    if (bytesRead != 3) break;

                    subBlockSize = (sizeBytes[0] & 0xFF) |
                            ((sizeBytes[1] & 0xFF) << 8) |
                            ((sizeBytes[2] & 0xFF) << 16);
                    headerSize = 4;
                } else {
                    byte[] sizeBuf = new byte[1];
                    source.read(currentPos + 1, sizeBuf, 0, 1);
                    subBlockSize = sizeBuf[0] & 0xFF;
                    if (sizeBuf[0] == -1) break;

                    subBlockSize = (subBlockSize << 1);
                    headerSize = 2;
                }

                if (subBlockSize < 0 || subBlockSize > blockEnd - currentPos) {
                    break;
                }

                int cleanId = subBlockId & SUBBLOCK_ID_MASK;
                if (METADATA_SUBBLOCKS.containsKey(cleanId)) {
                    long totalSubBlockSize = subBlockSize + headerSize;

                    tags.add(new TagInfo(TagFormat.WAVPACK_NATIVE, currentPos, totalSubBlockSize));

                    String subBlockName = METADATA_SUBBLOCKS.get(cleanId);
                    LOG.debug("Found WavPack metadata sub-block: {} at offset: {}, size: {} bytes",
                            subBlockName, currentPos, totalSubBlockSize);
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

}