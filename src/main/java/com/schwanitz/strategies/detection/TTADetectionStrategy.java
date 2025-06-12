package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Detection Strategy for TrueAudio (.tta) files
 * <p>
 * TrueAudio is a lossless audio codec with its own container structure.
 * Header Structure:
 * - Signature: "TTA1" (4 bytes) or "TTA2" (4 bytes)
 * - Audio Format: 16-bit Integer (2 bytes)
 * - Channels: 16-bit Integer (2 bytes)
 * - Bits Per Sample: 16-bit Integer (2 bytes)
 * - Sample Rate: 32-bit Integer (4 bytes)
 * - Data Length: 32-bit Integer (4 bytes)
 * - CRC32: 32-bit Integer (4 bytes)
 * <p>
 * TTA files typically use ID3v2/ID3v1/APE tags for metadata,
 * with rare native TTA metadata chunks.
 */
public class TTADetectionStrategy extends TagDetectionStrategy {

    private static final byte[] TTA1_SIGNATURE = {'T', 'T', 'A', '1'};
    private static final byte[] TTA2_SIGNATURE = {'T', 'T', 'A', '2'};
    private static final int TTA_HEADER_SIZE = 22;

    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.TTA_METADATA);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        return findTTASignature(startBuffer) != -1;
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath,
                                    byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        if (!canDetect(startBuffer, endBuffer)) {
            return tags;
        }

        Log.debug("Detecting TTA metadata in file: {}", filePath);

        try {
            long ttaHeaderOffset = findTTAHeaderOffset(file);

            if (ttaHeaderOffset == -1) {
                Log.debug("No TTA header found in file");
                return tags;
            }

            Log.debug("Found TTA header at offset: {}", ttaHeaderOffset);

            TTAHeader header = parseTTAHeader(file, ttaHeaderOffset);
            if (header == null) {
                Log.debug("Failed to parse TTA header");
                return tags;
            }

            Log.debug("TTA Header: version={}, channels={}, sampleRate={}",
                    header.version, header.channels, header.sampleRate);

            // Search for native TTA metadata (rare)
            long audioDataEnd = calculateAudioDataEnd(file, header, ttaHeaderOffset);
            List<TagInfo> nativeMetadata = findNativeTTAMetadata(file, audioDataEnd);
            tags.addAll(nativeMetadata);

        } catch (IOException e) {
            Log.error("Error detecting TTA metadata in {}: {}", filePath, e.getMessage());
            throw e;
        }

        return tags;
    }

    /**
     * Search for TTA signature in buffer
     */
    private int findTTASignature(byte[] buffer) {
        for (int i = 0; i <= buffer.length - 4; i++) {
            if (Arrays.equals(Arrays.copyOfRange(buffer, i, i + 4), TTA1_SIGNATURE) ||
                    Arrays.equals(Arrays.copyOfRange(buffer, i, i + 4), TTA2_SIGNATURE)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Find TTA header offset in file
     */
    private long findTTAHeaderOffset(RandomAccessFile file) throws IOException {
        long currentPos = 0;
        long fileLength = file.length();
        long searchLimit = Math.min(fileLength, 65536); // Search first 64KB

        while (currentPos + 4 <= searchLimit) {
            file.seek(currentPos);

            byte[] signature = new byte[4];
            int bytesRead = file.read(signature);

            if (bytesRead != 4) {
                break;
            }

            if (Arrays.equals(signature, TTA1_SIGNATURE) ||
                    Arrays.equals(signature, TTA2_SIGNATURE)) {
                return currentPos;
            }

            currentPos++;
        }

        return -1;
    }

    /**
     * Parse TTA header structure
     */
    private TTAHeader parseTTAHeader(RandomAccessFile file, long offset) throws IOException {
        file.seek(offset);

        byte[] signature = new byte[4];
        file.read(signature);

        String version;
        if (Arrays.equals(signature, TTA1_SIGNATURE)) {
            version = "TTA1";
        } else if (Arrays.equals(signature, TTA2_SIGNATURE)) {
            version = "TTA2";
        } else {
            return null;
        }

        int audioFormat = readLittleEndianShort(file);
        int channels = readLittleEndianShort(file);
        int bitsPerSample = readLittleEndianShort(file);
        long sampleRate = readLittleEndianInt(file) & 0xFFFFFFFFL;
        long dataLength = readLittleEndianInt(file) & 0xFFFFFFFFL;
        long crc32 = readLittleEndianInt(file) & 0xFFFFFFFFL;

        // Sanity checks
        if (channels < 1 || channels > 32) {
            Log.warn("Invalid TTA channel count: {}", channels);
            return null;
        }

        return new TTAHeader(version, audioFormat, channels, bitsPerSample, sampleRate, dataLength, crc32);
    }

    /**
     * Calculate approximate end of audio data
     */
    private long calculateAudioDataEnd(RandomAccessFile file, TTAHeader header, long headerOffset)
            throws IOException {
        // Use 90% of file size as approximation
        return (long)(file.length() * 0.9);
    }

    /**
     * Search for native TTA metadata chunks (very rare)
     */
    private List<TagInfo> findNativeTTAMetadata(RandomAccessFile file, long searchStart)
            throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        long currentPos = searchStart;
        long fileLength = file.length();
        long searchEnd = Math.max(searchStart, fileLength - 65536);

        while (currentPos + 8 < searchEnd) {
            file.seek(currentPos);

            byte[] chunkId = new byte[4];
            file.read(chunkId);

            String chunkString = new String(chunkId);

            // Hypothetical TTA metadata chunks
            if ("TTAM".equals(chunkString) || "META".equals(chunkString)) {
                int chunkSize = readLittleEndianInt(file);

                if (chunkSize > 0 && chunkSize < fileLength - currentPos - 8) {
                    tags.add(new TagInfo(TagFormat.TTA_METADATA, currentPos, chunkSize + 8));
                    Log.debug("Found TTA native metadata chunk at offset: {}, size: {} bytes",
                            currentPos, chunkSize + 8);

                    currentPos += 8 + chunkSize;
                } else {
                    currentPos += 4;
                }
            } else {
                currentPos += 4;
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
     * TTA Header data class
     */
    private static class TTAHeader {
        final String version;
        final int audioFormat;
        final int channels;
        final int bitsPerSample;
        final long sampleRate;
        final long dataLength;
        final long crc32;

        TTAHeader(String version, int audioFormat, int channels, int bitsPerSample,
                  long sampleRate, long dataLength, long crc32) {
            this.version = version;
            this.audioFormat = audioFormat;
            this.channels = channels;
            this.bitsPerSample = bitsPerSample;
            this.sampleRate = sampleRate;
            this.dataLength = dataLength;
            this.crc32 = crc32;
        }
    }
}