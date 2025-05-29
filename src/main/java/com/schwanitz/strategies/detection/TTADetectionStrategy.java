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
 * Detection Strategy für TrueAudio (.tta) Dateien
 *
 * TrueAudio ist ein verlustfreier Audio-Codec mit eigener Container-Struktur.
 *
 * TTA Header Struktur:
 * - Signature: "TTA1" (4 bytes) oder "TTA2" (4 bytes)
 * - Audio Format: 16-bit Integer (2 bytes)
 * - Channels: 16-bit Integer (2 bytes)
 * - Bits Per Sample: 16-bit Integer (2 bytes)
 * - Sample Rate: 32-bit Integer (4 bytes)
 * - Data Length: 32-bit Integer (4 bytes) - Anzahl Samples
 * - CRC32: 32-bit Integer (4 bytes)
 *
 * TTA Metadaten:
 * - ID3v2 Tags am Anfang der Datei (vor TTA Header)
 * - ID3v1 Tags am Ende der Datei
 * - APE Tags am Ende der Datei
 * - Native TTA Metadata (selten verwendet)
 *
 * TTA Format Versionen:
 * - TTA1: Original Format
 * - TTA2: Erweiterte Version (selten)
 */
public class TTADetectionStrategy extends TagDetectionStrategy {

    // TTA Signatures
    private static final byte[] TTA1_SIGNATURE = {'T', 'T', 'A', '1'};
    private static final byte[] TTA2_SIGNATURE = {'T', 'T', 'A', '2'};

    // TTA Header Size
    private static final int TTA_HEADER_SIZE = 22; // TTA1 Header
    private static final int TTA2_HEADER_SIZE = 22; // TTA2 Header (gleich)

    @Override
    public List<TagFormat> getSupportedFormats() {
        return List.of(TagFormat.TTA_METADATA);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        // TTA kann ID3v2 Tags am Anfang haben, daher müssen wir flexibel suchen
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
            // TTA Header Position finden
            long ttaHeaderOffset = findTTAHeaderOffset(file);

            if (ttaHeaderOffset == -1) {
                Log.debug("No TTA header found in file");
                return tags;
            }

            Log.debug("Found TTA header at offset: {}", ttaHeaderOffset);

            // TTA Header analysieren
            TTAHeader header = parseTTAHeader(file, ttaHeaderOffset);
            if (header == null) {
                Log.debug("Failed to parse TTA header");
                return tags;
            }

            Log.debug("TTA Header: version={}, format={}, channels={}, bps={}, sampleRate={}, dataLength={}",
                    header.version, header.audioFormat, header.channels,
                    header.bitsPerSample, header.sampleRate, header.dataLength);

            // Native TTA Metadata suchen (nach Audio-Daten)
            long audioDataEnd = calculateAudioDataEnd(file, header, ttaHeaderOffset);
            List<TagInfo> nativeMetadata = findNativeTTAMetadata(file, audioDataEnd);
            tags.addAll(nativeMetadata);

            // ID3v1/APE Tags am Ende suchen (falls nicht schon von anderen Strategien gefunden)
            List<TagInfo> endTags = findEndMetadata(file);
            tags.addAll(endTags);

            if (tags.isEmpty()) {
                Log.debug("No native TTA metadata found, relying on ID3/APE detection from other strategies");
            }

        } catch (IOException e) {
            Log.error("Error detecting TTA metadata in {}: {}", filePath, e.getMessage());
            throw e;
        }

        return tags;
    }

    /**
     * Sucht TTA Signature im Buffer
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
     * Findet TTA Header Offset in der Datei
     */
    private long findTTAHeaderOffset(RandomAccessFile file) throws IOException {
        // TTA Header kann nach ID3v2 Tags stehen
        long currentPos = 0;
        long fileLength = file.length();

        // Suche in den ersten 64KB der Datei
        long searchLimit = Math.min(fileLength, 65536);

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
     * Parst TTA Header
     */
    private TTAHeader parseTTAHeader(RandomAccessFile file, long offset) throws IOException {
        file.seek(offset);

        // Signature (4 bytes)
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

        // Audio Format (2 bytes, Little-Endian)
        int audioFormat = readLittleEndianShort(file);

        // Channels (2 bytes, Little-Endian)
        int channels = readLittleEndianShort(file);

        // Bits Per Sample (2 bytes, Little-Endian)
        int bitsPerSample = readLittleEndianShort(file);

        // Sample Rate (4 bytes, Little-Endian)
        long sampleRate = readLittleEndianInt(file) & 0xFFFFFFFFL;

        // Data Length - Anzahl Samples (4 bytes, Little-Endian)
        long dataLength = readLittleEndianInt(file) & 0xFFFFFFFFL;

        // CRC32 (4 bytes, Little-Endian)
        long crc32 = readLittleEndianInt(file) & 0xFFFFFFFFL;

        // Sanity checks
        if (audioFormat != 1 && audioFormat != 2) { // 1 = PCM, 2 = compressed
            Log.warn("Unusual TTA audio format: {}", audioFormat);
        }

        if (channels < 1 || channels > 32) {
            Log.warn("Invalid TTA channel count: {}", channels);
            return null;
        }

        if (bitsPerSample < 8 || bitsPerSample > 32) {
            Log.warn("Invalid TTA bits per sample: {}", bitsPerSample);
            return null;
        }

        if (sampleRate < 8000 || sampleRate > 192000) {
            Log.warn("Unusual TTA sample rate: {}", sampleRate);
        }

        return new TTAHeader(version, audioFormat, channels, bitsPerSample,
                sampleRate, dataLength, crc32);
    }

    /**
     * Berechnet das Ende der Audio-Daten
     */
    private long calculateAudioDataEnd(RandomAccessFile file, TTAHeader header, long headerOffset)
            throws IOException {
        // TTA verwendet eine Seek Table nach dem Header
        long seekTableOffset = headerOffset + TTA_HEADER_SIZE;

        // Für präzise Berechnung müssten wir die Seek Table parsen
        // Als Approximation nehmen wir 90% der Dateigröße
        long approximateDataEnd = (long)(file.length() * 0.9);

        return Math.max(seekTableOffset + 1000, approximateDataEnd);
    }

    /**
     * Sucht native TTA Metadaten nach den Audio-Daten
     */
    private List<TagInfo> findNativeTTAMetadata(RandomAccessFile file, long searchStart)
            throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        // TTA hat selten native Metadaten, meist werden ID3/APE verwendet
        // Hier könnten proprietäre TTA Metadata Chunks gesucht werden

        long currentPos = searchStart;
        long fileLength = file.length();

        // Suche in den letzten 64KB vor Ende (vor ID3v1/APE)
        long searchEnd = Math.max(searchStart, fileLength - 65536);

        while (currentPos + 8 < searchEnd) {
            file.seek(currentPos);

            // Hypothetische TTA Metadata Signature suchen
            byte[] chunkId = new byte[4];
            file.read(chunkId);

            String chunkString = new String(chunkId);

            // Bekannte TTA Metadata Chunks (hypothetisch - nicht standardisiert)
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
     * Sucht Metadaten am Dateiende (ID3v1, APE)
     * Nur als Fallback, normalerweise von anderen Strategien abgedeckt
     */
    private List<TagInfo> findEndMetadata(RandomAccessFile file) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        long fileLength = file.length();

        // Diese Methode ist primär als Dokumentation gedacht
        // In der Praxis werden ID3v1 und APE von anderen Strategien erkannt

        Log.debug("End metadata detection delegated to ID3V1DetectionStrategy and APEDetectionStrategy");

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
     * TTA Header Datenklasse
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