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
 * Detection Strategy für WavPack (.wv) Native Metadaten
 *
 * WavPack ist ein verlustfreier/verlustbehafteter Audio-Codec mit einem Block-basierten Format.
 *
 * WavPack Block Struktur:
 * - Block Header: 32 bytes
 *   - Magic: "wvpk" (4 bytes)
 *   - Block Size: 32-bit LE (4 bytes)
 *   - Version: 16-bit LE (2 bytes)
 *   - Track Length: 24-bit LE (3 bytes)
 *   - Block Index: 32-bit LE (4 bytes)
 *   - Total Samples: 32-bit LE (4 bytes)
 *   - Block Samples: 32-bit LE (4 bytes)
 *   - Flags: 32-bit LE (4 bytes)
 *   - CRC: 32-bit LE (4 bytes)
 *
 * WavPack Sub-Blocks (nach Header):
 * - ID_DUMMY: 0x0 (Padding)
 * - ID_ENCODER_INFO: 0x1 (Encoder Information)
 * - ID_DECORR_TERMS: 0x2 (Decorrelation Terms)
 * - ID_DECORR_WEIGHTS: 0x3 (Decorrelation Weights)
 * - ID_DECORR_SAMPLES: 0x4 (Decorrelation Samples)
 * - ID_ENTROPY_VARS: 0x5 (Entropy Variables)
 * - ID_HYBRID_PROFILE: 0x6 (Hybrid Profile)
 * - ID_SHAPING_WEIGHTS: 0x7 (Noise Shaping)
 * - ID_FLOAT_INFO: 0x8 (Float Normalization)
 * - ID_INT32_INFO: 0x9 (Integer Normalization)
 * - ID_WV_BITSTREAM: 0xa (WavPack Bitstream)
 * - ID_WVC_BITSTREAM: 0xb (WavPack Correction)
 * - ID_WVX_BITSTREAM: 0xc (WavPack Extension)
 * - ID_CHANNEL_INFO: 0xd (Channel Assignment)
 * - ID_RIFF_HEADER: 0x21 (RIFF Header Storage)
 * - ID_RIFF_TRAILER: 0x22 (RIFF Trailer/Tags)
 * - ID_ALT_HEADER: 0x23 (Alternative Header)
 * - ID_ALT_TRAILER: 0x24 (Alternative Trailer/Tags)
 * - ID_CONFIG_BLOCK: 0x25 (Configuration Block)
 * - ID_MD5_CHECKSUM: 0x26 (MD5 Audio Checksum)
 * - ID_SAMPLE_RATE: 0x27 (Extended Sample Rate)
 * - ID_ALT_EXTENSION: 0x28 (Alternative Extension)
 * - ID_ALT_MD5: 0x29 (Alternative MD5 Checksum)
 * - ID_NEW_CONFIG: 0x2a (New Configuration Block)
 * - ID_CHANNEL_IDENTITIES: 0x2b (Channel Identities Mask)
 * - ID_BLOCK_CHECKSUM: 0x2c (Block Audio Checksum)
 */
public class WavPackDetectionStrategy extends TagDetectionStrategy {

    // WavPack Magic Signature
    private static final byte[] WAVPACK_SIGNATURE = {'w', 'v', 'p', 'k'};

    // WavPack Block Header Size
    private static final int WAVPACK_HEADER_SIZE = 32;

    // Wichtige Sub-Block IDs für Metadaten
    private static final Map<Integer, String> METADATA_SUBBLOCKS = new HashMap<>();

    static {
        METADATA_SUBBLOCKS.put(0x21, "RIFF Header");        // Oft enthält WAV-Metadaten
        METADATA_SUBBLOCKS.put(0x22, "RIFF Trailer");       // Kann RIFF INFO/BWF enthalten
        METADATA_SUBBLOCKS.put(0x23, "Alternative Header");  // Alternative Metadaten
        METADATA_SUBBLOCKS.put(0x24, "Alternative Trailer"); // Alternative Tags
        METADATA_SUBBLOCKS.put(0x25, "Configuration Block"); // Encoder/Format Info
        METADATA_SUBBLOCKS.put(0x26, "MD5 Checksum");       // Audio Integrity
        METADATA_SUBBLOCKS.put(0x28, "Alternative Extension"); // Erweiterte Metadaten
        METADATA_SUBBLOCKS.put(0x29, "Alternative MD5");     // Alternative Checksums
        METADATA_SUBBLOCKS.put(0x2a, "New Configuration");   // Neue Config-Daten
        METADATA_SUBBLOCKS.put(0x2b, "Channel Identities");  // Kanal-Zuordnung
    }

    @Override
    public List<TagFormat> getSupportedFormats() {
        return List.of(TagFormat.WAVPACK_NATIVE);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (startBuffer.length < 4) {
            return false;
        }

        // WavPack Signature prüfen: "wvpk"
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
            final int MAX_BLOCKS = 10000; // Sanity limit

            // WavPack Blöcke durchlaufen
            while (currentPos + WAVPACK_HEADER_SIZE < fileLength && blockCount < MAX_BLOCKS) {
                file.seek(currentPos);

                // WavPack Block Header lesen
                WavPackBlockHeader header = parseBlockHeader(file);

                if (header == null) {
                    Log.debug("Invalid WavPack block header at position: {}", currentPos);
                    break;
                }

                Log.trace("WavPack Block {}: size={}, version={}, samples={}, flags=0x{}",
                        blockCount + 1, header.blockSize, header.version,
                        header.blockSamples, Integer.toHexString(header.flags));

                // Sub-Blocks in diesem Block durchsuchen
                List<TagInfo> blockTags = searchMetadataSubBlocks(file, currentPos + WAVPACK_HEADER_SIZE,
                        header.blockSize - WAVPACK_HEADER_SIZE);
                tags.addAll(blockTags);

                // Zum nächsten Block springen
                currentPos += header.blockSize;
                blockCount++;

                // Bei sehr großen Dateien: Nur erste 100 Blöcke analysieren
                if (blockCount >= 100 && fileLength > 100 * 1024 * 1024) { // > 100MB
                    Log.debug("Large WavPack file, limiting to first 100 blocks");
                    break;
                }
            }

            if (blockCount >= MAX_BLOCKS) {
                Log.warn("Reached maximum WavPack block limit ({}) in file: {}", MAX_BLOCKS, filePath);
            }

            Log.debug("WavPack native metadata detection completed: found {} metadata sub-blocks in {} blocks",
                    tags.size(), blockCount);

        } catch (IOException e) {
            Log.error("Error detecting WavPack metadata in {}: {}", filePath, e.getMessage());
            throw e;
        }

        return tags;
    }

    /**
     * Parst WavPack Block Header
     */
    private WavPackBlockHeader parseBlockHeader(RandomAccessFile file) throws IOException {
        // Magic "wvpk" (4 bytes)
        byte[] magic = new byte[4];
        file.read(magic);

        if (!Arrays.equals(magic, WAVPACK_SIGNATURE)) {
            return null;
        }

        // Block Size (4 bytes, Little-Endian) - enthält Header
        long blockSize = readLittleEndianInt(file) & 0xFFFFFFFFL;

        // Version (2 bytes, Little-Endian)
        int version = readLittleEndianShort(file);

        // Track Length (3 bytes, Little-Endian)
        byte[] trackLengthBytes = new byte[3];
        file.read(trackLengthBytes);
        int trackLength = (trackLengthBytes[0] & 0xFF) |
                ((trackLengthBytes[1] & 0xFF) << 8) |
                ((trackLengthBytes[2] & 0xFF) << 16);

        // Reserved (1 byte)
        file.skipBytes(1);

        // Block Index (4 bytes, Little-Endian)
        long blockIndex = readLittleEndianInt(file) & 0xFFFFFFFFL;

        // Total Samples (4 bytes, Little-Endian)
        long totalSamples = readLittleEndianInt(file) & 0xFFFFFFFFL;

        // Block Samples (4 bytes, Little-Endian)
        long blockSamples = readLittleEndianInt(file) & 0xFFFFFFFFL;

        // Flags (4 bytes, Little-Endian)
        long flags = readLittleEndianInt(file) & 0xFFFFFFFFL;

        // CRC (4 bytes, Little-Endian)
        long crc = readLittleEndianInt(file) & 0xFFFFFFFFL;

        // Sanity checks
        if (blockSize < WAVPACK_HEADER_SIZE || blockSize > 10 * 1024 * 1024) { // Max 10MB
            Log.warn("Invalid WavPack block size: {}", blockSize);
            return null;
        }

        if (version < 0x402 || version > 0x410) { // Bekannte Versionen: 4.02 - 4.16
            Log.debug("Unusual WavPack version: 0x{}", Integer.toHexString(version));
        }

        return new WavPackBlockHeader(blockSize, version, trackLength, blockIndex,
                totalSamples, blockSamples, (int)flags, (int)crc);
    }

    /**
     * Durchsucht Sub-Blocks nach Metadaten
     */
    private List<TagInfo> searchMetadataSubBlocks(RandomAccessFile file, long blockDataStart,
                                                  long blockDataSize) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        long currentPos = blockDataStart;
        long blockEnd = blockDataStart + blockDataSize;

        while (currentPos + 2 < blockEnd) {
            file.seek(currentPos);

            try {
                // Sub-Block Header lesen (mindestens 2 bytes)
                int subBlockId = file.read(); // 1 byte
                if (subBlockId == -1) break;

                int subBlockSize;

                // Sub-Block Size Format abhängig von ID
                if ((subBlockId & 0x80) != 0) {
                    // Large sub-block: 3-byte size
                    byte[] sizeBytes = new byte[3];
                    int bytesRead = file.read(sizeBytes);
                    if (bytesRead != 3) break;

                    subBlockSize = (sizeBytes[0] & 0xFF) |
                            ((sizeBytes[1] & 0xFF) << 8) |
                            ((sizeBytes[2] & 0xFF) << 16);
                    currentPos += 4; // ID + 3-byte size
                } else {
                    // Small sub-block: 1-byte size
                    subBlockSize = file.read();
                    if (subBlockSize == -1) break;

                    subBlockSize = (subBlockSize << 1); // Size is stored as words
                    currentPos += 2; // ID + 1-byte size
                }

                if (subBlockSize < 0 || subBlockSize > blockEnd - currentPos) {
                    Log.debug("Invalid WavPack sub-block size: {} at position: {}", subBlockSize, currentPos);
                    break;
                }

                // Metadaten-relevante Sub-Blocks identifizieren
                int cleanId = subBlockId & 0x7F; // Remove large flag bit
                if (METADATA_SUBBLOCKS.containsKey(cleanId)) {
                    long subBlockStart = currentPos - (((subBlockId & 0x80) != 0) ? 4 : 2);
                    long totalSubBlockSize = subBlockSize + (((subBlockId & 0x80) != 0) ? 4 : 2);

                    tags.add(new TagInfo(TagFormat.WAVPACK_NATIVE, subBlockStart, totalSubBlockSize));

                    String subBlockName = METADATA_SUBBLOCKS.get(cleanId);
                    Log.debug("Found WavPack metadata sub-block: {} (ID: 0x{}) at offset: {}, size: {} bytes",
                            subBlockName, Integer.toHexString(cleanId), subBlockStart, totalSubBlockSize);
                }

                // Zum nächsten Sub-Block springen
                currentPos += subBlockSize;

                // Word-Alignment für Sub-Blocks
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
     * Liest einen 16-bit Little-Endian Short
     */
    private int readLittleEndianShort(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[2];
        file.read(bytes);
        return ((bytes[0] & 0xFF)) | ((bytes[1] & 0xFF) << 8);
    }

    /**
     * Liest einen 32-bit Little-Endian Integer
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
     * WavPack Block Header Datenklasse
     */
    private static class WavPackBlockHeader {
        final long blockSize;
        final int version;
        final int trackLength;
        final long blockIndex;
        final long totalSamples;
        final long blockSamples;
        final int flags;
        final int crc;

        WavPackBlockHeader(long blockSize, int version, int trackLength, long blockIndex,
                           long totalSamples, long blockSamples, int flags, int crc) {
            this.blockSize = blockSize;
            this.version = version;
            this.trackLength = trackLength;
            this.blockIndex = blockIndex;
            this.totalSamples = totalSamples;
            this.blockSamples = blockSamples;
            this.flags = flags;
            this.crc = crc;
        }
    }

    /**
     * Gibt alle bekannten WavPack Metadata Sub-Block IDs zurück
     */
    public static Map<Integer, String> getKnownMetadataSubBlocks() {
        return new HashMap<>(METADATA_SUBBLOCKS);
    }

    /**
     * Prüft ob eine Sub-Block ID Metadaten enthält
     */
    public static boolean isMetadataSubBlock(int subBlockId) {
        return METADATA_SUBBLOCKS.containsKey(subBlockId & 0x7F);
    }
}