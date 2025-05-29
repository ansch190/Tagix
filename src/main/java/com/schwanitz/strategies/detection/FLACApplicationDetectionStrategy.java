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
 * Detection Strategy for FLAC Application Blocks
 * <p>
 * FLAC Application Blocks allow applications to store proprietary metadata.
 * Structure:
 * - FLAC signature: "fLaC" (4 bytes)
 * - Metadata blocks with type and size
 * - Application blocks have type 2
 * - Application ID (4 bytes) + application data
 * <p>
 * Known Application IDs include ATCH, BSOL, RAGA (ReplayGain), etc.
 */
public class FLACApplicationDetectionStrategy extends TagDetectionStrategy {

    // Known FLAC Application IDs (32-bit Big-Endian)
    private static final Map<Integer, String> KNOWN_APPLICATION_IDS = new HashMap<>();

    static {
        KNOWN_APPLICATION_IDS.put(0x41544348, "Audio Tag (ATCH)");
        KNOWN_APPLICATION_IDS.put(0x42534F4C, "beSolo (BSOL)");
        KNOWN_APPLICATION_IDS.put(0x46696361, "CUETools.NET (Fica)");
        KNOWN_APPLICATION_IDS.put(0x52414741, "ReplayGain Analysis (RAGA)");
        KNOWN_APPLICATION_IDS.put(0x55554944, "UUID Application Block (UUID)");
    }

    @Override
    public List<TagFormat> getSupportedFormats() {
        return List.of(TagFormat.FLAC_APPLICATION);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (startBuffer.length < 4) {
            return false;
        }
        return startBuffer[0] == 'f' && startBuffer[1] == 'L' &&
                startBuffer[2] == 'a' && startBuffer[3] == 'C';
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath,
                                    byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        if (!canDetect(startBuffer, endBuffer)) {
            return tags;
        }

        Log.debug("Detecting FLAC Application blocks in file: {}", filePath);

        try {
            long position = 4; // After "fLaC" signature
            boolean isLastBlock = false;
            int blockCount = 0;
            final int MAX_BLOCKS = 1000;

            while (!isLastBlock && position < file.length() && blockCount < MAX_BLOCKS) {
                file.seek(position);

                byte[] blockHeader = new byte[4];
                int bytesRead = file.read(blockHeader);

                if (bytesRead != 4) {
                    Log.debug("Incomplete FLAC block header at position: {}", position);
                    break;
                }

                isLastBlock = (blockHeader[0] & 0x80) != 0;
                int blockType = blockHeader[0] & 0x7F;
                int blockLength = ((blockHeader[1] & 0xFF) << 16) |
                        ((blockHeader[2] & 0xFF) << 8) |
                        (blockHeader[3] & 0xFF);

                if (blockLength < 0 || blockLength > file.length() - position - 4) {
                    Log.warn("Invalid FLAC block length: {} at position: {}", blockLength, position);
                    break;
                }

                // Block Type 2 = APPLICATION Block
                if (blockType == 2) {
                    if (blockLength >= 4) {
                        tags.add(new TagInfo(TagFormat.FLAC_APPLICATION, position, blockLength + 4));

                        // Extract Application ID for logging
                        long appIdPos = position + 4;
                        file.seek(appIdPos);
                        byte[] appIdBytes = new byte[4];
                        file.read(appIdBytes);

                        int appId = ((appIdBytes[0] & 0xFF) << 24) |
                                ((appIdBytes[1] & 0xFF) << 16) |
                                ((appIdBytes[2] & 0xFF) << 8) |
                                (appIdBytes[3] & 0xFF);

                        String appName = KNOWN_APPLICATION_IDS.getOrDefault(appId,
                                String.format("Unknown (0x%08X)", appId));

                        Log.debug("Found FLAC Application Block: {} at offset: {}, size: {} bytes",
                                appName, position, blockLength + 4);
                    }
                }

                position += 4 + blockLength;
                blockCount++;
            }

            Log.debug("FLAC Application detection completed: found {} blocks", tags.size());

        } catch (IOException e) {
            Log.error("Error detecting FLAC Application blocks in {}: {}", filePath, e.getMessage());
            throw e;
        }

        return tags;
    }
}