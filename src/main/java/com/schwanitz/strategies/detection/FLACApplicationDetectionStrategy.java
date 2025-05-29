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
 * Detection Strategy für FLAC Application Blocks
 *
 * FLAC Application Blocks ermöglichen es Anwendungen, proprietäre Metadaten zu speichern.
 * Bekannte Application IDs:
 * - 0x41544348 ("ATCH") - Jon Johansen's Audio Tag
 * - 0x42534F4C ("BSOL") - beSolo
 * - 0x46696361 ("Fica") - CUETools.NET
 * - 0x46746F6C ("Ftol") - Flac Tools
 * - 0x4D4F544C ("MOTL") - Media Jukebox
 * - 0x4D50434B ("MPCK") - MPC (Musepack)
 * - 0x4D555345 ("MUSE") - Muse Research Receptor
 * - 0x52414741 ("RAGA") - ReplayGain Analysis
 * - 0x53484E54 ("SHNT") - Shorten
 * - 0x53696C6B ("Silk") - Silk Purse
 * - 0x534F4E59 ("SONY") - Sony Creative Software
 * - 0x5351455A ("SQEZ") - SqrSoft Advanced Compressor
 * - 0x54745776 ("TtWv") - TwistedWave
 * - 0x55554944 ("UUID") - UUID Application Block
 * - 0x58420000 ("XB\0\0") - XBAT
 * - 0x786D6364 ("xmcd") - xmcd
 */
public class FLACApplicationDetectionStrategy extends TagDetectionStrategy {

    // Bekannte FLAC Application IDs (32-bit Big-Endian)
    private static final Map<Integer, String> KNOWN_APPLICATION_IDS = new HashMap<>();

    static {
        KNOWN_APPLICATION_IDS.put(0x41544348, "Audio Tag (ATCH)");
        KNOWN_APPLICATION_IDS.put(0x42534F4C, "beSolo (BSOL)");
        KNOWN_APPLICATION_IDS.put(0x46696361, "CUETools.NET (Fica)");
        KNOWN_APPLICATION_IDS.put(0x46746F6C, "Flac Tools (Ftol)");
        KNOWN_APPLICATION_IDS.put(0x4D4F544C, "Media Jukebox (MOTL)");
        KNOWN_APPLICATION_IDS.put(0x4D50434B, "Musepack (MPCK)");
        KNOWN_APPLICATION_IDS.put(0x4D555345, "Muse Research Receptor (MUSE)");
        KNOWN_APPLICATION_IDS.put(0x52414741, "ReplayGain Analysis (RAGA)");
        KNOWN_APPLICATION_IDS.put(0x53484E54, "Shorten (SHNT)");
        KNOWN_APPLICATION_IDS.put(0x536C6B21, "Silk Purse (Slk!)");
        KNOWN_APPLICATION_IDS.put(0x534F4E59, "Sony Creative Software (SONY)");
        KNOWN_APPLICATION_IDS.put(0x5351455A, "SqrSoft Advanced Compressor (SQEZ)");
        KNOWN_APPLICATION_IDS.put(0x54745776, "TwistedWave (TtWv)");
        KNOWN_APPLICATION_IDS.put(0x55554944, "UUID Application Block (UUID)");
        KNOWN_APPLICATION_IDS.put(0x58420000, "XBAT (XB)");
        KNOWN_APPLICATION_IDS.put(0x786D6364, "xmcd (xmcd)");
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

        // FLAC Signature prüfen: "fLaC" (0x664C6143)
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
            // FLAC Stream beginnt nach "fLaC" Signature
            long position = 4;
            boolean isLastBlock = false;
            int blockCount = 0;
            final int MAX_BLOCKS = 1000; // Sanity limit

            while (!isLastBlock && position < file.length() && blockCount < MAX_BLOCKS) {
                file.seek(position);

                // FLAC Metadata Block Header (4 bytes)
                byte[] blockHeader = new byte[4];
                int bytesRead = file.read(blockHeader);

                if (bytesRead != 4) {
                    Log.debug("Incomplete FLAC block header at position: {}", position);
                    break;
                }

                // Last-metadata-block flag (Bit 7 des ersten Bytes)
                isLastBlock = (blockHeader[0] & 0x80) != 0;

                // Block Type (untere 7 Bits des ersten Bytes)
                int blockType = blockHeader[0] & 0x7F;

                // Block Length (24-bit Big-Endian, bytes 1-3)
                int blockLength = ((blockHeader[1] & 0xFF) << 16) |
                        ((blockHeader[2] & 0xFF) << 8) |
                        (blockHeader[3] & 0xFF);

                Log.trace("FLAC Block: type={}, length={}, isLast={}, position={}",
                        blockType, blockLength, isLastBlock, position);

                // Sanity checks
                if (blockLength < 0 || blockLength > file.length() - position - 4) {
                    Log.warn("Invalid FLAC block length: {} at position: {}", blockLength, position);
                    break;
                }

                // Block Type 2 = APPLICATION Block
                if (blockType == 2) {
                    if (blockLength >= 4) { // Mindestens 4 Bytes für Application ID
                        tags.add(new TagInfo(TagFormat.FLAC_APPLICATION, position, blockLength + 4));

                        // Application ID für Debug-Logging extrahieren
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
                    } else {
                        Log.warn("FLAC Application block too small: {} bytes", blockLength);
                    }
                }

                // Zur nächsten Block-Position springen
                position += 4 + blockLength;
                blockCount++;
            }

            if (blockCount >= MAX_BLOCKS) {
                Log.warn("Reached maximum FLAC block limit ({}) in file: {}", MAX_BLOCKS, filePath);
            }

            Log.debug("FLAC Application detection completed: found {} blocks", tags.size());

        } catch (IOException e) {
            Log.error("Error detecting FLAC Application blocks in {}: {}", filePath, e.getMessage());
            throw e;
        }

        return tags;
    }

    /**
     * Utility method um Application ID zu einem lesbaren String zu konvertieren
     */
    public static String getApplicationName(int applicationId) {
        return KNOWN_APPLICATION_IDS.getOrDefault(applicationId,
                String.format("Unknown Application (0x%08X)", applicationId));
    }

    /**
     * Konvertiert 4 Bytes zu einem Application ID Integer (Big-Endian)
     */
    public static int bytesToApplicationId(byte[] bytes) {
        if (bytes.length != 4) {
            throw new IllegalArgumentException("Application ID must be exactly 4 bytes");
        }

        return ((bytes[0] & 0xFF) << 24) |
                ((bytes[1] & 0xFF) << 16) |
                ((bytes[2] & 0xFF) << 8) |
                (bytes[3] & 0xFF);
    }

    /**
     * Prüft ob eine Application ID bekannt/dokumentiert ist
     */
    public static boolean isKnownApplicationId(int applicationId) {
        return KNOWN_APPLICATION_IDS.containsKey(applicationId);
    }

    /**
     * Gibt alle bekannten Application IDs zurück
     */
    public static Map<Integer, String> getKnownApplicationIds() {
        return new HashMap<>(KNOWN_APPLICATION_IDS);
    }
}