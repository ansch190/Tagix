package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.FormatDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class OggFlacDetectionStrategy implements FormatDetectionStrategy {
    private static final Logger LOGGER = Logger.getLogger(OggFlacDetectionStrategy.class.getName());
    private static final int MAX_OGG_PAGES = 10;

    @Override
    public boolean canDetect(String fileExtension, byte[] startBuffer, byte[] endBuffer) {
        String signature = startBuffer.length >= 4 ? new String(startBuffer, 0, 4) : "";
        boolean isOGG = (fileExtension.equals("ogg") || fileExtension.equals("spx") || fileExtension.equals("opus")) && signature.equals("OggS");
        boolean isFLAC = fileExtension.equals("flac") && signature.equals("fLaC");
        return isOGG || isFLAC;
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> detectedTags = new ArrayList<>();

        // OGG/FLAC: Vorbis Comments (Exakt die gleiche Logik wie vorher)
        TagInfo vorbisTag = checkVorbisComment(file);
        if (vorbisTag != null) {
            detectedTags.add(vorbisTag);
        }

        return detectedTags;
    }

    // Ursprüngliche checkVorbisComment Methode unverändert
    private static TagInfo checkVorbisComment(RandomAccessFile file) throws IOException {
        try {
            file.seek(0);
            byte[] buffer = new byte[4];
            file.read(buffer);
            if (new String(buffer).equals("OggS")) {
                // OGG: Suche gezielt nach Comment Header (Vorbis, Speex, Opus)
                long position = 0;
                int pageCount = 0;
                while (position + 27 < file.length() && pageCount < MAX_OGG_PAGES) {
                    file.seek(position);
                    file.read(buffer); // OggS
                    if (!new String(buffer).equals("OggS")) {
                        break;
                    }
                    file.seek(position + 26);
                    byte[] segmentTable = new byte[1];
                    file.read(segmentTable);
                    int segmentCount = segmentTable[0] & 0xFF;
                    byte[] segments = new byte[segmentCount];
                    file.read(segments);
                    int pageSize = 27 + segmentCount;
                    for (byte segment : segments) {
                        pageSize += (segment & 0xFF);
                    }

                    // Prüfe, ob dies der Comment Header ist
                    long commentOffset = position + 27 + segmentCount;
                    file.seek(commentOffset);
                    byte[] packetType = new byte[1];
                    file.read(packetType);
                    if (packetType[0] == 3 || (packetType[0] == 1 && position > 0)) { // Comment Header
                        byte[] headerId = new byte[8];
                        file.read(headerId);
                        String header = new String(headerId);
                        if (header.startsWith("vorbis") || header.startsWith("Speex") || header.equals("OpusTags")) {
                            return new TagInfo(TagFormat.VORBIS_COMMENT, commentOffset, pageSize - (commentOffset - position));
                        }
                    }
                    position += pageSize;
                    pageCount++;
                }
                LOGGER.fine("Kein Vorbis/Speex/Opus-Comment-Header in OGG-Datei gefunden");
                return null;
            } else if (new String(buffer).equals("fLaC")) {
                // FLAC: Prüfe alle Metadatenblöcke
                long position = 4;
                while (position < file.length()) {
                    file.seek(position);
                    byte[] blockHeader = new byte[4];
                    file.read(blockHeader);
                    boolean isLast = (blockHeader[0] & 0x80) != 0;
                    int blockType = blockHeader[0] & 0x7F;
                    int blockLength = ((blockHeader[1] & 0xFF) << 16) |
                            ((blockHeader[2] & 0xFF) << 8) | (blockHeader[3] & 0xFF);
                    if (blockType == 4) { // Vorbis Comment Block
                        return new TagInfo(TagFormat.VORBIS_COMMENT, position, blockLength + 4);
                    }
                    position += 4 + blockLength;
                    if (isLast) {
                        break;
                    }
                }
                LOGGER.fine("Kein Vorbis-Comment-Block in FLAC-Datei gefunden");
                return null;
            }
            return null;
        } catch (IOException e) {
            LOGGER.warning("Fehler bei Vorbis-Comment-Prüfung: " + e.getMessage());
            return null;
        }
    }
}