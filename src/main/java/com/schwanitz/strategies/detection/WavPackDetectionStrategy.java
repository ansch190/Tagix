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

    /**
     * {@inheritDoc}
     * <p>
     * Gibt das unterstützte WavPack-Format zurück: WAVPACK_NATIVE.
     */
    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.WAVPACK_NATIVE);
    }

    /**
     * Prüft, ob die Dateidaten eine WavPack-Datei enthalten, anhand des
     * "wvpk"-Kennzeichens am Dateianfang.
     *
     * @param startBuffer Puffer mit den ersten Bytes der Datei (mindestens 4 Bytes)
     * @param endBuffer   Puffer mit den letzten Bytes der Datei (nicht verwendet)
     * @return {@code true}, wenn das WavPack-Kennzeichen erkannt wurde
     */
    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (startBuffer.length < 4) {
            return false;
        }
        return Arrays.equals(Arrays.copyOfRange(startBuffer, 0, 4), WAVPACK_SIGNATURE);
    }

    /**
     * Analysiert die WavPack-Datei und durchsucht Blöcke nach Metadaten-Unterblöcken.
     * <p>
     * Durchläuft Top-Level-Blöcke und prüft nur Blöcke mit dem INITIAL_BLOCK-Flag
     * auf Metadaten-Unterblöcke.
     *
     * @param file        die geöffnete Datei
     * @param filePath    der Dateipfad zur Protokollierung
     * @param startBuffer Puffer mit den ersten Bytes der Datei
     * @param endBuffer   Puffer mit den letzten Bytes der Datei
     * @return eine Liste der erkannten Metadaten-{@link TagInfo}-Objekte
     * @throws IOException wenn ein Fehler beim Lesen der Datei auftritt
     */
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
     * Durchsucht Unterblöcke nach Metadaten innerhalb eines einzelnen WavPack-Blocks.
     * <p>
     * Wird nur für Blöcke aufgerufen, die wahrscheinlich Metadaten enthalten (Initialblöcke).
     *
     * @param file            die geöffnete Datei
     * @param blockDataStart  die Startposition der Blockdaten (nach dem 32-Byte-Header)
     * @param blockDataSize   die Größe der Blockdaten
     * @return eine Liste der gefundenen Metadaten-{@link TagInfo}-Objekte
     * @throws IOException wenn ein Fehler beim Lesen auftritt
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