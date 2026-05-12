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

    /**
     * {@inheritDoc}
     * <p>
     * Gibt das unterstützte TTA-Format zurück: TTA_METADATA.
     */
    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.TTA_METADATA);
    }

    /**
     * Prüft, ob die Dateidaten eine TrueAudio-Datei enthalten, anhand der
     * "TTA1"- oder "TTA2"-Kennzeichen. Sucht im Startpuffer nach der Signatur.
     *
     * @param startBuffer Puffer mit den ersten Bytes der Datei
     * @param endBuffer   Puffer mit den letzten Bytes der Datei (nicht verwendet)
     * @return {@code true}, wenn eine TTA-Signatur erkannt wurde
     */
    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        return findTTASignature(startBuffer) != -1;
    }

    /**
     * Analysiert die TTA-Datei und validiert den Header.
     * <p>
     * Da TTA keine nativen Metadaten-Chunks hat, fungiert diese Methode als
     * Formaterkenner und gibt eine leere Liste zurück. Die eigentlichen Tags
     * (ID3v2, ID3v1, APE) werden von den entsprechenden Strategien erkannt.
     *
     * @param file        die geöffnete Datei
     * @param filePath    der Dateipfad zur Protokollierung
     * @param startBuffer Puffer mit den ersten Bytes der Datei
     * @param endBuffer   Puffer mit den letzten Bytes der Datei
     * @return eine leere Liste (TTA hat keine nativen Metadaten)
     * @throws IOException wenn ein Fehler beim Lesen der Datei auftritt
     */
    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath,
                                    byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        if (!canDetect(startBuffer, endBuffer)) {
            return tags;
        }

        LOG.debug("Detecting TTA metadata in file: {}", filePath);

        try {
            long ttaHeaderOffset = findTTAHeaderOffset(file);

            if (ttaHeaderOffset == -1) {
                LOG.debug("No TTA header found in file");
                return tags;
            }

            LOG.debug("Found TTA header at offset: {}", ttaHeaderOffset);

            TTAHeader header = parseTTAHeader(file, ttaHeaderOffset);
            if (header == null) {
                LOG.debug("Failed to parse TTA header");
                return tags;
            }

            LOG.debug("TTA Header: version={}, channels={}, sampleRate={}",
                    header.version, header.channels, header.sampleRate);

        } catch (IOException e) {
            LOG.error("Error detecting TTA metadata in {}: {}", filePath, e.getMessage());
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

    private long findTTAHeaderOffset(RandomAccessFile file) throws IOException {
        long currentPos = 0;
        long fileLength = file.length();
        long searchLimit = Math.min(fileLength, HEADER_SEARCH_LIMIT);

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

        if (channels < 1 || channels > MAX_CHANNELS) {
            LOG.warn("Invalid TTA channel count: {}", channels);
            return null;
        }

        return new TTAHeader(version, audioFormat, channels, bitsPerSample, sampleRate, dataLength, crc32);
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