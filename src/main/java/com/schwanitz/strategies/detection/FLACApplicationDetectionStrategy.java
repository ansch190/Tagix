package com.schwanitz.strategies.detection;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
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

    private static final Map<Integer, String> KNOWN_APPLICATION_IDS = new HashMap<>();

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

    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.FLAC_APPLICATION);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (startBuffer.length < FLAC_SIGNATURE_LENGTH) {
            return false;
        }
        return startBuffer[0] == 'f' && startBuffer[1] == 'L' &&
                startBuffer[2] == 'a' && startBuffer[3] == 'C';
    }

    @Override
    public List<TagInfo> detectTags(SeekableDataSource source, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        if (!canDetect(startBuffer, endBuffer)) {
            return tags;
        }

        LOG.debug("Detecting FLAC Application blocks in source: {}", source.name());

        try {
            long sourceLength = source.length();
            long position = FLAC_SIGNATURE_LENGTH;
            boolean isLastBlock = false;
            int blockCount = 0;

            while (!isLastBlock && position < sourceLength && blockCount < MAX_BLOCKS) {
                byte[] blockHeader = new byte[FLAC_BLOCK_HEADER_SIZE];
                int bytesRead = source.read(position, blockHeader, 0, FLAC_BLOCK_HEADER_SIZE);

                if (bytesRead != FLAC_BLOCK_HEADER_SIZE) {
                    LOG.debug("Incomplete FLAC block header at position: {}", position);
                    break;
                }

                isLastBlock = (blockHeader[0] & FLAC_LAST_BLOCK_FLAG) != 0;
                int blockType = blockHeader[0] & FLAC_BLOCK_TYPE_MASK;
                int blockLength = ((blockHeader[1] & 0xFF) << 16) |
                        ((blockHeader[2] & 0xFF) << 8) |
                        (blockHeader[3] & 0xFF);

                if (blockLength < 0 || blockLength > sourceLength - position - FLAC_BLOCK_HEADER_SIZE) {
                    LOG.warn("Invalid FLAC block length: {} at position: {}", blockLength, position);
                    break;
                }

                if (blockType == FLAC_APPLICATION_BLOCK_TYPE) {
                    if (blockLength >= FLAC_APPLICATION_ID_SIZE) {
                        tags.add(new TagInfo(TagFormat.FLAC_APPLICATION, position, blockLength + FLAC_BLOCK_HEADER_SIZE));

                        byte[] appIdBytes = new byte[FLAC_APPLICATION_ID_SIZE];
                        source.read(position + FLAC_BLOCK_HEADER_SIZE, appIdBytes, 0, FLAC_APPLICATION_ID_SIZE);

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
            LOG.error("Error detecting FLAC Application blocks in {}", source.name(), e);
            throw e;
        }

        return tags;
    }
}