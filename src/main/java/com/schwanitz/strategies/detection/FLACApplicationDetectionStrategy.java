package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Erkennungsstrategie für FLAC-Application-Blöcke.
 * <p>
 * FLAC-Application-Blöcke ermöglichen es Anwendungen, proprietäre Metadaten zu speichern.
 * Struktur:
 * <ul>
 *   <li>FLAC-Kennzeichen: "fLaC" (4 Bytes)</li>
 *   <li>Metadatenblöcke mit Typ und Größe</li>
 *   <li>Application-Blöcke haben den Typ 2</li>
 *   <li>Application-ID (4 Bytes) + Anwendungsdaten</li>
 * </ul>
 * <p>
 * Bekannte Application-IDs sind unter anderem ATCH, BSOL, RAGA (ReplayGain) usw.
 */
public class FLACApplicationDetectionStrategy extends TagDetectionStrategy {

    // Known FLAC Application IDs (32-bit Big-Endian)
    private static final Map<Integer, String> KNOWN_APPLICATION_IDS = new HashMap<>();

    // FLAC structural constants
    private static final int FLAC_SIGNATURE_LENGTH = 4;
    private static final int FLAC_BLOCK_HEADER_SIZE = 4;
    private static final int FLAC_APPLICATION_BLOCK_TYPE = 2;
    private static final int FLAC_APPLICATION_ID_SIZE = 4;
    private static final int MAX_BLOCKS = 1000;
    private static final int FLAC_LAST_BLOCK_FLAG = 0x80;
    private static final int FLAC_BLOCK_TYPE_MASK = 0x7F;

    static {
        KNOWN_APPLICATION_IDS.put(0x41544348, "Audio Tag (ATCH)");
        KNOWN_APPLICATION_IDS.put(0x42534F4C, "beSolo (BSOL)");
        KNOWN_APPLICATION_IDS.put(0x46696361, "CUETools.NET (Fica)");
        KNOWN_APPLICATION_IDS.put(0x52414741, "ReplayGain Analysis (RAGA)");
        KNOWN_APPLICATION_IDS.put(0x55554944, "UUID Application Block (UUID)");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Gibt das unterstützte FLAC-Format zurück: FLAC_APPLICATION.
     */
    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.FLAC_APPLICATION);
    }

    /**
     * Prüft, ob die Dateidaten eine FLAC-Datei enthalten, anhand des
     * "fLaC"-Kennzeichens am Dateianfang.
     *
     * @param startBuffer Puffer mit den ersten Bytes der Datei (mindestens 4 Bytes)
     * @param endBuffer   Puffer mit den letzten Bytes der Datei (nicht verwendet)
     * @return {@code true}, wenn das FLAC-Kennzeichen erkannt wurde
     */
    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (startBuffer.length < FLAC_SIGNATURE_LENGTH) {
            return false;
        }
        return startBuffer[0] == 'f' && startBuffer[1] == 'L' &&
                startBuffer[2] == 'a' && startBuffer[3] == 'C';
    }

    /**
     * Analysiert die FLAC-Datei und durchsucht alle Metadatenblöcke nach
     * Application-Blöcken (Typ 2).
     * <p>
     * Ermittelt die Application-ID jedes gefundenen Blocks und ordnet sie
     * bekannten Anwendungen zu.
     *
     * @param file        die geöffnete Datei
     * @param filePath    der Dateipfad zur Protokollierung
     * @param startBuffer Puffer mit den ersten Bytes der Datei
     * @param endBuffer   Puffer mit den letzten Bytes der Datei
     * @return eine Liste der erkannten {@link TagInfo}-Objekte für Application-Blöcke
     * @throws IOException wenn ein Fehler beim Lesen der Datei auftritt
     */
    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath,
                                    byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        if (!canDetect(startBuffer, endBuffer)) {
            return tags;
        }

        LOG.debug("Detecting FLAC Application blocks in file: {}", filePath);

        try {
            long position = FLAC_SIGNATURE_LENGTH;
            boolean isLastBlock = false;
            int blockCount = 0;

            while (!isLastBlock && position < file.length() && blockCount < MAX_BLOCKS) {
                file.seek(position);

                byte[] blockHeader = new byte[FLAC_BLOCK_HEADER_SIZE];
                int bytesRead = file.read(blockHeader);

                if (bytesRead != FLAC_BLOCK_HEADER_SIZE) {
                    LOG.debug("Incomplete FLAC block header at position: {}", position);
                    break;
                }

                isLastBlock = (blockHeader[0] & FLAC_LAST_BLOCK_FLAG) != 0;
                int blockType = blockHeader[0] & FLAC_BLOCK_TYPE_MASK;
                int blockLength = ((blockHeader[1] & 0xFF) << 16) |
                        ((blockHeader[2] & 0xFF) << 8) |
                        (blockHeader[3] & 0xFF);

                if (blockLength < 0 || blockLength > file.length() - position - FLAC_BLOCK_HEADER_SIZE) {
                    LOG.warn("Invalid FLAC block length: {} at position: {}", blockLength, position);
                    break;
                }

                if (blockType == FLAC_APPLICATION_BLOCK_TYPE) {
                    if (blockLength >= FLAC_APPLICATION_ID_SIZE) {
                        tags.add(new TagInfo(TagFormat.FLAC_APPLICATION, position, blockLength + FLAC_BLOCK_HEADER_SIZE));

                        long appIdPos = position + FLAC_BLOCK_HEADER_SIZE;
                        file.seek(appIdPos);
                        byte[] appIdBytes = new byte[FLAC_APPLICATION_ID_SIZE];
                        file.read(appIdBytes);

                        int appId = ((appIdBytes[0] & 0xFF) << 24) |
                                ((appIdBytes[1] & 0xFF) << 16) |
                                ((appIdBytes[2] & 0xFF) << 8) |
                                (appIdBytes[3] & 0xFF);

                        String appName = KNOWN_APPLICATION_IDS.getOrDefault(appId,
                                String.format("Unknown (0x%08X)", appId));

                        LOG.debug("Found FLAC Application Block: {} at offset: {}, size: {} bytes",
                                appName, position, blockLength + FLAC_BLOCK_HEADER_SIZE);
                    }
                }

                position += FLAC_BLOCK_HEADER_SIZE + blockLength;
                blockCount++;
            }

            LOG.debug("FLAC Application detection completed: found {} blocks", tags.size());

        } catch (IOException e) {
            LOG.error("Error detecting FLAC Application blocks in {}: {}", filePath, e.getMessage());
            throw e;
        }

        return tags;
    }
}