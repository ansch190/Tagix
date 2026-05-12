package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Erkennungsstrategie für Lyrics3-Tags (Versionen 1 und 2).
 * <p>
 * Lyrics3-Tags befinden sich am Ende von Dateien, vor etwaigen ID3v1-Tags.
 * <p>
 * Lyrics3v1:
 * <ul>
 *   <li>Start: "LYRICSBEGIN"</li>
 *   <li>Liedtext-Inhalt (variable Länge)</li>
 *   <li>Ende: "LYRICSEND"</li>
 * </ul>
 * <p>
 * Lyrics3v2:
 * <ul>
 *   <li>Start: "LYRICSBEGIN"</li>
 *   <li>Feldinhalt mit Größeninformationen</li>
 *   <li>Größenangabe (6 Ziffern)</li>
 *   <li>Ende: "LYRICS200"</li>
 * </ul>
 */
public class Lyrics3DetectionStrategy extends TagDetectionStrategy {

    private static final int LYRICS3_END_TAG_LENGTH = 9;
    private static final int LYRICS3_BEGIN_TAG_LENGTH = 11;
    private static final int LYRICS3V2_SIZE_LENGTH = 6;
    private static final int LYRICS3V2_FOOTER_SIZE = LYRICS3_END_TAG_LENGTH + LYRICS3V2_SIZE_LENGTH;

    /**
     * {@inheritDoc}
     * <p>
     * Gibt die unterstützten Lyrics3-Formate zurück: LYRICS3V1, LYRICS3V2.
     */
    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.LYRICS3V1, TagFormat.LYRICS3V2);
    }

    /**
     * Prüft, ob die Dateidaten ein Lyrics3-Tag enthalten, anhand der
     * Endekennzeichen "LYRICSEND" (v1) oder "LYRICS200" (v2) am Dateiende.
     *
     * @param startBuffer Puffer mit den ersten Bytes der Datei (nicht verwendet)
     * @param endBuffer   Puffer mit den letzten Bytes der Datei (mindestens 9 Bytes)
     * @return {@code true}, wenn ein Lyrics3-Endekennzeichen gefunden wurde
     */
    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (endBuffer.length < LYRICS3_END_TAG_LENGTH) {
            return false;
        }
        String endTag = new String(endBuffer, endBuffer.length - LYRICS3_END_TAG_LENGTH, LYRICS3_END_TAG_LENGTH, StandardCharsets.US_ASCII);
        return endTag.equals("LYRICSEND") || endTag.equals("LYRICS200");
    }

    /**
     * Analysiert das Lyrics3-Tag und ermittelt die genaue Version (v1 oder v2),
     * Position und Größe.
     * <p>
     * Unterscheidet anhand des Endekennzeichens zwischen Lyrics3v1 ("LYRICSEND")
     * und Lyrics3v2 ("LYRICS200").
     *
     * @param file        die geöffnete Datei
     * @param filePath    der Dateipfad zur Protokollierung
     * @param startBuffer Puffer mit den ersten Bytes der Datei
     * @param endBuffer   Puffer mit den letzten Bytes der Datei
     * @return eine Liste mit maximal einem {@link TagInfo}-Objekt
     * @throws IOException wenn ein Fehler beim Lesen der Datei auftritt
     */
    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        if (endBuffer.length < LYRICS3_END_TAG_LENGTH) {
            return tags;
        }

        String endTag = new String(endBuffer, endBuffer.length - LYRICS3_END_TAG_LENGTH, LYRICS3_END_TAG_LENGTH, StandardCharsets.US_ASCII);
        if (endTag.equals("LYRICS200")) {
            tags.addAll(detectLyrics3v2(file, endBuffer));
        } else if (endTag.equals("LYRICSEND")) {
            tags.addAll(detectLyrics3v1(file));
        }
        return tags;
    }

    private List<TagInfo> detectLyrics3v2(RandomAccessFile file, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        long fileLength = file.length();

        String sizeStr = readSizeFromEndBuffer(endBuffer);
        if (sizeStr == null) {
            sizeStr = readSizeFromFile(file, fileLength);
        }

        if (sizeStr == null) {
            return tags;
        }

        try {
            int size = Integer.parseInt(sizeStr);
            long startOffset = fileLength - LYRICS3V2_FOOTER_SIZE - size;
            if (startOffset >= 0) {
                file.seek(startOffset);
                byte[] buffer = new byte[LYRICS3_BEGIN_TAG_LENGTH];
                file.read(buffer);
                if (new String(buffer, StandardCharsets.US_ASCII).equals("LYRICSBEGIN")) {
                    tags.add(new TagInfo(TagFormat.LYRICS3V2, startOffset, size + LYRICS3V2_FOOTER_SIZE));
                }
            }
        } catch (NumberFormatException e) {
            LOG.debug("Invalid Lyrics3v2 size indication: {}", sizeStr);
        }

        return tags;
    }

    private String readSizeFromEndBuffer(byte[] endBuffer) {
        if (endBuffer.length >= LYRICS3V2_FOOTER_SIZE) {
            return new String(endBuffer, endBuffer.length - LYRICS3V2_FOOTER_SIZE, LYRICS3V2_SIZE_LENGTH, StandardCharsets.US_ASCII);
        }
        return null;
    }

    private String readSizeFromEndBuffer4KBFallback(RandomAccessFile file, long fileLength) throws IOException {
        int readSize = (int) Math.min(fileLength, 16384);
        file.seek(fileLength - readSize);
        byte[] largerBuffer = new byte[readSize];
        file.read(largerBuffer);
        return new String(largerBuffer, largerBuffer.length - LYRICS3V2_FOOTER_SIZE, LYRICS3V2_SIZE_LENGTH, StandardCharsets.US_ASCII);
    }

    private String readSizeFromFile(RandomAccessFile file, long fileLength) throws IOException {
        file.seek(fileLength - LYRICS3V2_FOOTER_SIZE);
        byte[] sizeBytes = new byte[LYRICS3V2_FOOTER_SIZE];
        int bytesRead = file.read(sizeBytes);
        if (bytesRead >= LYRICS3V2_SIZE_LENGTH) {
            return new String(sizeBytes, 0, LYRICS3V2_SIZE_LENGTH, StandardCharsets.US_ASCII);
        }
        return null;
    }

    private List<TagInfo> detectLyrics3v1(RandomAccessFile file) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        long fileLength = file.length();
        long position = fileLength - LYRICS3_END_TAG_LENGTH - LYRICS3_BEGIN_TAG_LENGTH;
        if (position >= 0) {
            file.seek(position);
            byte[] buffer = new byte[LYRICS3_BEGIN_TAG_LENGTH];
            file.read(buffer);
            if (new String(buffer, StandardCharsets.US_ASCII).equals("LYRICSBEGIN")) {
                long tagSize = fileLength - position;
                tags.add(new TagInfo(TagFormat.LYRICS3V1, position, tagSize));
            }
        }
        return tags;
    }
}