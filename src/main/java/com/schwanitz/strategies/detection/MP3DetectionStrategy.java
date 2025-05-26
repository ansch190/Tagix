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

public class MP3DetectionStrategy implements FormatDetectionStrategy {
    private static final Logger Log = LoggerFactory.getLogger(MP3DetectionStrategy.class);
    private static final int BUFFER_SIZE = 4096;

    @Override
    public boolean canDetect(String fileExtension, byte[] startBuffer, byte[] endBuffer) {
        return fileExtension.equals("mp3");
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> detectedTags = new ArrayList<>();

        // MP3: ID3v2 → ID3v1 → Lyrics3 → APE (Exakt die gleiche Logik wie vorher)
        TagInfo id3v2Start = checkID3v2FromBuffer(startBuffer, 0);
        if (id3v2Start != null) {
            detectedTags.add(id3v2Start);
        }

        TagInfo id3v1Tag = checkID3v1FromBuffer(endBuffer, file.length() - 128);
        if (id3v1Tag != null) {
            detectedTags.add(id3v1Tag);
        }

        // Lyrics3 nur prüfen, wenn kein ID3v1
        if (!containsFormat(detectedTags, TagFormat.ID3V1) && !containsFormat(detectedTags, TagFormat.ID3V1_1)) {
            TagInfo lyrics3Tag = checkLyrics3FromBuffer(endBuffer, file.length() - (id3v1Tag != null ? 128 : 0), filePath);
            if (lyrics3Tag != null) {
                detectedTags.add(lyrics3Tag);
            }
        }

        TagInfo apeStart = checkAPEFromBuffer(startBuffer, 0);
        if (apeStart != null) {
            detectedTags.add(apeStart);
        }
        TagInfo apeEnd = checkAPEFromBuffer(endBuffer, file.length() - 32);
        if (apeEnd != null && !containsFormat(detectedTags, apeEnd.getFormat())) {
            detectedTags.add(apeEnd);
        }

        return detectedTags;
    }

    // Alle ursprünglichen Hilfsmethoden unverändert übernommen
    private static boolean containsFormat(List<TagInfo> tags, TagFormat format) {
        return tags.stream().anyMatch(tag -> tag.getFormat() == format);
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

    private static TagInfo checkAPEFromBuffer(byte[] buffer, long position) {
        if (position < 0 || position + 32 > buffer.length) {
            Log.debug("Ungültige Position für APE: " + position);
            return null;
        }
        try {
            int bufferOffset = (int) position;
            if (!new String(buffer, bufferOffset, 8).equals("APETAGEX")) {
                return null;
            }

            // Versionsnummer prüfen (Bytes 8-11, Little-Endian)
            int version = (buffer[bufferOffset + 8] & 0xFF) | ((buffer[bufferOffset + 9] & 0xFF) << 8) |
                    ((buffer[bufferOffset + 10] & 0xFF) << 16) | ((buffer[bufferOffset + 11] & 0xFF) << 24);
            // Tag-Größe prüfen (Bytes 12-15)
            int tagSize = (buffer[bufferOffset + 12] & 0xFF) | ((buffer[bufferOffset + 13] & 0xFF) << 8) |
                    ((buffer[bufferOffset + 14] & 0xFF) << 16) | ((buffer[bufferOffset + 15] & 0xFF) << 24);
            if (tagSize < 32) {
                Log.debug("Ungültige APE-Tag-Größe: " + tagSize);
                return null;
            }

            TagFormat format = (version == 1000) ? TagFormat.APEV1 : (version == 2000) ? TagFormat.APEV2 : null;
            if (format == null) {
                Log.debug("Unbekannte APE-Version: " + version);
                return null;
            }
            return new TagInfo(format, position, tagSize);
        } catch (Exception e) {
            Log.warn("Fehler bei APE-Prüfung aus Puffer: " + e.getMessage());
            return null;
        }
    }

    private static TagInfo checkLyrics3FromBuffer(byte[] buffer, long startPosition, String filePath) throws IOException {
        try {
            // Prüfe auf Lyrics3v2-Signatur ("LYRICS200")
            if (startPosition >= 15 && buffer.length >= 15) {
                long endOffset = startPosition - 15;
                int bufferOffset = (int) (endOffset % BUFFER_SIZE);
                if (bufferOffset + 15 <= buffer.length) {
                    String endTag = new String(buffer, bufferOffset + 6, 9);
                    if (endTag.equals("LYRICS200")) {
                        // Prüfe Größenangabe
                        String sizeStr = new String(buffer, bufferOffset, 6);
                        try {
                            int size = Integer.parseInt(sizeStr);
                            if (size < 0 || size > startPosition - 15) {
                                Log.debug("Ungültige Lyrics3v2-Größe: " + size);
                                return null;
                            }
                            // Prüfe Start-Signatur
                            try (RandomAccessFile file = new RandomAccessFile(filePath, "r")) {
                                long startOffset = startPosition - 15 - size;
                                file.seek(startOffset);
                                byte[] startBuffer = new byte[11];
                                file.read(startBuffer);
                                if (new String(startBuffer).equals("LYRICSBEGIN")) {
                                    return new TagInfo(TagFormat.LYRICS3V2, startOffset, size + 15);
                                }
                            }
                        } catch (NumberFormatException e) {
                            Log.debug("Ungültige Lyrics3v2-Größenangabe: " + sizeStr);
                            return null;
                        }
                    }
                }
            }

            // Prüfe auf Lyrics3v1-Signatur ("LYRICSEND")
            if (startPosition >= 9 && buffer.length >= 9) {
                long endOffset = startPosition - 9;
                int bufferOffset = (int) (endOffset % BUFFER_SIZE);
                if (bufferOffset + 9 <= buffer.length) {
                    String endTag = new String(buffer, bufferOffset, 9);
                    if (endTag.equals("LYRICSEND")) {
                        // Suche nach "LYRICSBEGIN"
                        try (RandomAccessFile file = new RandomAccessFile(filePath, "r")) {
                            long pos = startPosition - 9 - 11;
                            if (pos >= 0) {
                                file.seek(pos);
                                byte[] startBuffer = new byte[11];
                                file.read(startBuffer);
                                if (new String(startBuffer).equals("LYRICSBEGIN")) {
                                    return new TagInfo(TagFormat.LYRICS3V1, pos, startPosition - pos);
                                }
                            }
                        }
                    }
                }
            }

            Log.debug("Kein Lyrics3-Tag gefunden");
            return null;
        } catch (Exception e) {
            Log.warn("Fehler bei Lyrics3-Prüfung aus Puffer: " + e.getMessage());
            return null;
        }
    }
}