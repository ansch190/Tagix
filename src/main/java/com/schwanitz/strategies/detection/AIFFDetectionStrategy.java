package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.FormatDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

public class AIFFDetectionStrategy implements FormatDetectionStrategy {

    private static final Logger Log = LoggerFactory.getLogger(AIFFDetectionStrategy.class);

    @Override
    public boolean canDetect(String fileExtension, byte[] startBuffer, byte[] endBuffer) {
        String signature = startBuffer.length >= 4 ? new String(startBuffer, 0, 4) : "";
        boolean isAIFF = (fileExtension.equals("aiff") || fileExtension.equals("aif")) && signature.equals("FORM");
        return isAIFF;
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException {
        // AIFF: AIFF Metadata und ID3 kombiniert (Exakt die gleiche Logik wie vorher)
        return checkAIFFTags(file);
    }

    // Ursprüngliche checkAIFFTags Methode unverändert
    private static List<TagInfo> checkAIFFTags(RandomAccessFile file) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        try {
            file.seek(0);
            byte[] buffer = new byte[12];
            file.read(buffer);
            if (!new String(buffer, 0, 4).equals("FORM")) {
                Log.debug("Kein AIFF-FORM-Header gefunden");
                return tags;
            }
            if (!new String(buffer, 8, 4).equals("AIFF") && !new String(buffer, 8, 4).equals("AIFC")) {
                Log.debug("Datei ist kein AIFF-Format: " + new String(buffer, 8, 4));
                return tags;
            }

            // Parse Chunkstruktur, um Metadaten- und ID3-Chunks zu finden
            long position = 12; // Nach FORM-Header
            while (position + 8 < file.length()) {
                file.seek(position);
                byte[] chunkHeader = new byte[8];
                file.read(chunkHeader);
                String chunkType = new String(chunkHeader, 0, 4);
                int chunkSize = ((chunkHeader[4] & 0xFF) << 24) | ((chunkHeader[5] & 0xFF) << 16) |
                        ((chunkHeader[6] & 0xFF) << 8) | (chunkHeader[7] & 0xFF);

                if (chunkSize < 0 || chunkSize > file.length() - position - 8) {
                    Log.debug("Ungültige Chunkgröße: " + chunkSize + " an Position " + position);
                    return tags;
                }

                if (chunkType.equals("NAME") || chunkType.equals("AUTH") || chunkType.equals("ANNO") || chunkType.equals("(c) ")) {
                    tags.add(new TagInfo(TagFormat.AIFF_METADATA, position, chunkSize + 8));
                } else if (chunkType.equals("ID3 ")) {
                    TagInfo id3Tag = checkID3v2(file, position + 8);
                    if (id3Tag != null) {
                        tags.add(new TagInfo(id3Tag.getFormat(), position, chunkSize + 8));
                    }
                }
                position += 8 + chunkSize + (chunkSize % 2); // Padding-Byte
            }
        } catch (IOException e) {
            Log.warn("Fehler bei AIFF-Tag-Prüfung: " + e.getMessage());
        }
        return tags;
    }

    // Hilfsmethode von ursprünglicher Implementierung
    private static TagInfo checkID3v2(RandomAccessFile file, long position) throws IOException {
        if (position < 0 || position + 10 > file.length()) {
            Log.debug("Ungültige Position für ID3v2: " + position);
            return null;
        }
        try {
            file.seek(position);
            byte[] buffer = new byte[10];
            file.read(buffer);
            if (!new String(buffer, 0, 3).equals("ID3")) {
                return null;
            }

            // Versionsbytes prüfen (Byte 3 und 4)
            int majorVersion = buffer[3] & 0xFF;
            if (buffer[4] != 0) {
                Log.debug("Ungültige Revision für ID3v2: " + (buffer[4] & 0xFF));
                return null;
            }

            // Größe prüfen (synchrone Größe, Bytes 6-9)
            int size = ((buffer[6] & 0x7F) << 21) | ((buffer[7] & 0x7F) << 14) |
                    ((buffer[8] & 0x7F) << 7) | (buffer[9] & 0xFF);
            if (size < 0 || size > file.length() - position) {
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
        } catch (IOException e) {
            Log.warn("Fehler bei ID3v2-Prüfung an Position " + position + ": " + e.getMessage());
            return null;
        }
    }
}