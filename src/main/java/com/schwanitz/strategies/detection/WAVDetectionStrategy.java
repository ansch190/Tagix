package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.FormatDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WAVDetectionStrategy implements FormatDetectionStrategy {

    private static final Logger Log = LoggerFactory.getLogger(WAVDetectionStrategy.class);

    private static final int BUFFER_SIZE = 4096;

    @Override
    public boolean canDetect(String fileExtension, byte[] startBuffer, byte[] endBuffer) {
        String signature = startBuffer.length >= 4 ? new String(startBuffer, 0, 4) : "";
        return fileExtension.equals("wav") && signature.equals("RIFF");
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> detectedTags = new ArrayList<>();

        // WAV: RIFF INFO und BWF kombiniert → ID3v2 → ID3v1 (Exakt die gleiche Logik wie vorher)
        detectedTags.addAll(checkRIFFTags(file));

        TagInfo id3v2Start = checkID3v2FromBuffer(startBuffer, 0);
        if (id3v2Start != null) {
            detectedTags.add(id3v2Start);
        }

        TagInfo id3v1Tag = checkID3v1FromBuffer(endBuffer, file.length() - 128);
        if (id3v1Tag != null) {
            detectedTags.add(id3v1Tag);
        }

        return detectedTags;
    }

    // Ursprüngliche checkRIFFTags Methode unverändert
    private static List<TagInfo> checkRIFFTags(RandomAccessFile file) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        try {
            file.seek(0);
            byte[] buffer = new byte[12];
            file.read(buffer);
            if (!new String(buffer, 0, 4).equals("RIFF")) {
                Log.debug("Kein RIFF-Header gefunden");
                return tags;
            }
            if (!new String(buffer, 8, 4).equals("WAVE")) {
                Log.debug("Datei ist kein WAV-Format: " + new String(buffer, 8, 4));
                return tags;
            }

            // Parse Chunkstruktur, um LIST-INFO und bext-Chunk zu finden
            long position = 12; // Nach RIFF-Header
            while (position + 8 < file.length()) {
                file.seek(position);
                byte[] chunkHeader = new byte[8];
                file.read(chunkHeader);
                String chunkType = new String(chunkHeader, 0, 4);
                int chunkSize = (chunkHeader[4] & 0xFF) | ((chunkHeader[5] & 0xFF) << 8) |
                        ((chunkHeader[6] & 0xFF) << 16) | ((chunkHeader[7] & 0xFF) << 24);

                if (chunkSize < 0 || chunkSize > file.length() - position - 8) {
                    Log.debug("Ungültige Chunkgröße: " + chunkSize + " an Position " + position);
                    return tags;
                }

                if (chunkType.equals("LIST")) {
                    // Prüfe, ob es ein INFO-Chunk ist
                    file.seek(position + 8);
                    byte[] listType = new byte[4];
                    file.read(listType);
                    if (new String(listType).equals("INFO")) {
                        tags.add(new TagInfo(TagFormat.RIFF_INFO, position, chunkSize + 8));
                    }
                } else if (chunkType.equals("bext")) {
                    // Prüfe Version des bext-Chunks
                    if (chunkSize < 602) {
                        Log.debug("Ungültige bext-Chunkgröße: " + chunkSize);
                    } else {
                        file.seek(position + 8 + 602);
                        byte[] versionBuffer = new byte[2];
                        file.read(versionBuffer);
                        int version = (versionBuffer[0] & 0xFF) | ((versionBuffer[1] & 0xFF) << 8);
                        TagFormat format;
                        switch (version) {
                            case 0:
                                format = TagFormat.BWF_V0;
                                break;
                            case 1:
                                format = TagFormat.BWF_V1;
                                break;
                            case 2:
                                format = TagFormat.BWF_V2;
                                break;
                            default:
                                Log.debug("Unbekannte BWF-Version: " + version);
                                format = null;
                                break;
                        }
                        if (format != null) {
                            tags.add(new TagInfo(format, position, chunkSize + 8));
                        }
                    }
                }
                position += 8 + chunkSize + (chunkSize % 2); // Padding-Byte
            }
        } catch (IOException e) {
            Log.warn("Fehler bei RIFF-Tag-Prüfung: " + e.getMessage());
        }
        return tags;
    }

    // Hilfsmethoden von der ursprünglichen Implementierung
    private static TagInfo checkID3v2FromBuffer(byte[] buffer, long position) {
        if (position < 0 || position + 10 > buffer.length) {
            Log.debug("Ungültige Position für ID3v2: " + position);
            return null;
        }
        try {
            int bufferOffset = (int) position;
            if (!new String(buffer, bufferOffset, 3).equals("ID3")) {
                return null;
            }

            // Versionsbytes prüfen (Byte 3 und 4)
            int majorVersion = buffer[bufferOffset + 3] & 0xFF;
            if (buffer[bufferOffset + 4] != 0) {
                Log.debug("Ungültige Revision für ID3v2: " + (buffer[bufferOffset + 4] & 0xFF));
                return null;
            }

            // Größe prüfen (synchrone Größe, Bytes 6-9)
            int size = ((buffer[bufferOffset + 6] & 0x7F) << 21) | ((buffer[bufferOffset + 7] & 0x7F) << 14) |
                    ((buffer[bufferOffset + 8] & 0x7F) << 7) | (buffer[bufferOffset + 9] & 0xFF);
            if (size < 0) {
                Log.debug("Ungültige ID3v2-Größe: " + size);
                return null;
            }

            TagFormat format;
            switch (majorVersion) {
                case 2:
                    format = TagFormat.ID3V2_2;
                    break;
                case 3:
                    format = TagFormat.ID3V2_3;
                    break;
                case 4:
                    format = TagFormat.ID3V2_4;
                    break;
                default:
                    Log.debug("Unbekannte ID3v2-Version: " + majorVersion);
                    return null;
            }
            return new TagInfo(format, position, size + 10);
        } catch (Exception e) {
            Log.warn("Fehler bei ID3v2-Prüfung aus Puffer: " + e.getMessage());
            return null;
        }
    }

    private static TagInfo checkID3v1FromBuffer(byte[] buffer, long offset) {
        if (buffer.length < 128 || offset < 0) {
            Log.debug("Puffer zu klein für ID3v1 oder ungültiger Offset: " + offset);
            return null;
        }
        try {
            int bufferOffset = (int) (offset % BUFFER_SIZE);
            if (bufferOffset + 128 > buffer.length) {
                return null;
            }
            if (!new String(buffer, bufferOffset, 3).equals("TAG")) {
                return null;
            }

            // Prüfung auf ID3v1.1 (Track-Nummer)
            int trackOffset = bufferOffset + 125;
            TagFormat format = (trackOffset + 2 <= buffer.length && buffer[trackOffset] == 0 && buffer[trackOffset + 1] != 0)
                    ? TagFormat.ID3V1_1 : TagFormat.ID3V1;
            return new TagInfo(format, offset, 128);
        } catch (Exception e) {
            Log.warn("Fehler bei ID3v1-Prüfung aus Puffer: " + e.getMessage());
            return null;
        }
    }
}