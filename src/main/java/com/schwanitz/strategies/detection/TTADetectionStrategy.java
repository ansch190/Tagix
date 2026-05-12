package com.schwanitz.strategies.detection;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Erkennungsstrategie für TrueAudio-(.tta)-Dateien.
 * <p>
 * TrueAudio ist ein verlustfreier Audio-Codec mit eigener Containerstruktur.
 * Header-Struktur:
 * <ul>
 *   <li>Kennzeichen: "TTA1" (4 Bytes) oder "TTA2" (4 Bytes)</li>
 *   <li>Audioformat: 16-Bit-Integer (2 Bytes)</li>
 *   <li>Kanäle: 16-Bit-Integer (2 Bytes)</li>
 *   <li>Bits pro Sample: 16-Bit-Integer (2 Bytes)</li>
 *   <li>Abtastrate: 32-Bit-Integer (4 Bytes)</li>
 *   <li>Datenlänge: 32-Bit-Integer (4 Bytes)</li>
 *   <li>CRC32: 32-Bit-Integer (4 Bytes)</li>
 * </ul>
 * <p>
 * TTA-Dateien verwenden ID3v2/ID3v1/APE-Tags für Metadaten.
 * Das TTA1-Format hat keine nativen Metadaten-Chunks; diese Strategie validiert
 * nur den TTA-Header und gibt keine Tags zurück, sondern fungiert als Formaterkenner.
 */
public class TTADetectionStrategy extends TagDetectionStrategy {

    private static final byte[] TTA1_SIGNATURE = {'T', 'T', 'A', '1'};
    private static final byte[] TTA2_SIGNATURE = {'T', 'T', 'A', '2'};
    private static final int TTA_HEADER_SIZE = 22;
    private static final int MAX_CHANNELS = 32;
    private static final long HEADER_SEARCH_LIMIT = 65536;

    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.TTA_METADATA);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        return findTTASignature(startBuffer) != -1;
    }

    @Override
    public List<TagInfo> detectTags(SeekableDataSource source, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        if (!canDetect(startBuffer, endBuffer)) {
            return tags;
        }

        LOG.debug("Detecting TTA metadata in source: {}", source.name());

        try {
            long ttaHeaderOffset = findTTAHeaderOffset(source);

            if (ttaHeaderOffset == -1) {
                LOG.debug("No TTA header found in source");
                return tags;
            }

            LOG.debug("Found TTA header at offset: {}", ttaHeaderOffset);

            TTAHeader header = parseTTAHeader(source, ttaHeaderOffset);
            if (header == null) {
                LOG.debug("Failed to parse TTA header");
                return tags;
            }

            LOG.debug("TTA Header: version={}, channels={}, sampleRate={}",
                    header.version, header.channels, header.sampleRate);

        } catch (IOException e) {
            LOG.error("Error detecting TTA metadata in {}: {}", source.name(), e.getMessage());
            throw e;
        }

        return tags;
    }

    private int findTTASignature(byte[] buffer) {
        for (int i = 0; i <= buffer.length - 4; i++) {
            if (Arrays.equals(Arrays.copyOfRange(buffer, i, i + 4), TTA1_SIGNATURE) ||
                    Arrays.equals(Arrays.copyOfRange(buffer, i, i + 4), TTA2_SIGNATURE)) {
                return i;
            }
        }
        return -1;
    }

    private long findTTAHeaderOffset(SeekableDataSource source) throws IOException {
        long currentPos = 0;
        long sourceLength = source.length();
        long searchLimit = Math.min(sourceLength, HEADER_SEARCH_LIMIT);

        while (currentPos + 4 <= searchLimit) {
            byte[] signature = new byte[4];
            int bytesRead = source.read(currentPos, signature, 0, 4);
            if (bytesRead != 4) break;

            if (Arrays.equals(signature, TTA1_SIGNATURE) ||
                    Arrays.equals(signature, TTA2_SIGNATURE)) {
                return currentPos;
            }

            currentPos++;
        }

        return -1;
    }

    private TTAHeader parseTTAHeader(SeekableDataSource source, long offset) throws IOException {
        byte[] headerBytes = new byte[TTA_HEADER_SIZE];
        int bytesRead = source.read(offset, headerBytes, 0, TTA_HEADER_SIZE);
        if (bytesRead != TTA_HEADER_SIZE) return null;

        String version;
        if (Arrays.equals(Arrays.copyOfRange(headerBytes, 0, 4), TTA1_SIGNATURE)) {
            version = "TTA1";
        } else if (Arrays.equals(Arrays.copyOfRange(headerBytes, 0, 4), TTA2_SIGNATURE)) {
            version = "TTA2";
        } else {
            return null;
        }

        int audioFormat = readLittleEndianShort(headerBytes, 4);
        int channels = readLittleEndianShort(headerBytes, 6);
        int bitsPerSample = readLittleEndianShort(headerBytes, 8);
        long sampleRate = readLittleEndianInt(headerBytes, 10) & 0xFFFFFFFFL;
        long dataLength = readLittleEndianInt(headerBytes, 14) & 0xFFFFFFFFL;
        long crc32 = readLittleEndianInt(headerBytes, 18) & 0xFFFFFFFFL;

        if (channels < 1 || channels > MAX_CHANNELS) {
            LOG.warn("Invalid TTA channel count: {}", channels);
            return null;
        }

        return new TTAHeader(version, audioFormat, channels, bitsPerSample, sampleRate, dataLength, crc32);
    }

    private int readLittleEndianShort(byte[] data, int offset) {
        return ((data[offset] & 0xFF)) | ((data[offset + 1] & 0xFF) << 8);
    }

    private int readLittleEndianInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF)) |
                ((data[offset + 1] & 0xFF) << 8) |
                ((data[offset + 2] & 0xFF) << 16) |
                ((data[offset + 3] & 0xFF) << 24);
    }

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