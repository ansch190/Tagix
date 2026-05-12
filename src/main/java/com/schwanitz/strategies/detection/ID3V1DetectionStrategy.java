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
 * Erkennungsstrategie für ID3v1- und ID3v1.1-Tags.
 * <p>
 * ID3v1-Tags befinden sich immer an den letzten 128 Bytes einer Datei.
 * Struktur:
 * <ul>
 *   <li>Kennzeichen: "TAG" (3 Bytes)</li>
 *   <li>Titel: 30 Bytes</li>
 *   <li>Interpret: 30 Bytes</li>
 *   <li>Album: 30 Bytes</li>
 *   <li>Jahr: 4 Bytes</li>
 *   <li>Kommentar: 30 Bytes (ID3v1) oder 28 Bytes + Titelnummer (ID3v1.1)</li>
 *   <li>Genre: 1 Byte</li>
 * </ul>
 * <p>
 * ID3v1.1 wird von ID3v1 durch ein Null-Byte an Position 125 gefolgt von
 * der Titelnummer an Position 126 unterschieden.
 */
public class ID3V1DetectionStrategy extends TagDetectionStrategy {

    private static final int ID3V1_SIZE = 128;

    /**
     * {@inheritDoc}
     * <p>
     * Gibt die unterstützten ID3v1-Formate zurück: ID3V1, ID3V1_1.
     */
    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.ID3V1, TagFormat.ID3V1_1);
    }

    /**
     * Prüft, ob die Dateidaten ein ID3v1-Tag enthalten, anhand des "TAG"-Kennzeichens
     * am Dateiende.
     *
     * @param startBuffer Puffer mit den ersten Bytes der Datei (nicht verwendet)
     * @param endBuffer   Puffer mit den letzten Bytes der Datei (mindestens 128 Bytes)
     * @return {@code true}, wenn das ID3v1-Kennzeichen gefunden wurde
     */
    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (endBuffer.length < ID3V1_SIZE) {
            return false;
        }
        return new String(endBuffer, endBuffer.length - ID3V1_SIZE, 3, StandardCharsets.US_ASCII).equals("TAG");
    }

    /**
     * Analysiert das ID3v1-Tag und ermittelt die genaue Version (v1 oder v1.1),
     * Position und Größe.
     * <p>
     * ID3v1.1 wird erkannt, wenn an Position 125 (relativ zum Tag-Anfang) ein Null-Byte
     * gefolgt von einer Nicht-Null-Titelnummer steht.
     *
     * @param file        die geöffnete Datei
     * @param filePath    der Dateipfad zur Protokollierung
     * @param startBuffer Puffer mit den ersten Bytes der Datei
     * @param endBuffer   Puffer mit den letzten Bytes der Datei
     * @return eine Liste mit genau einem {@link TagInfo}-Objekt für das erkannte ID3v1-Tag
     * @throws IOException wenn ein Fehler beim Lesen der Datei auftritt
     */
    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        if (canDetect(startBuffer, endBuffer)) {
            long offset = file.length() - ID3V1_SIZE;
            int trackOffset = endBuffer.length - ID3V1_SIZE + 125;
            if (endBuffer[trackOffset] == 0 && endBuffer[trackOffset + 1] != 0) {
                tags.add(new TagInfo(TagFormat.ID3V1_1, offset, ID3V1_SIZE));
            } else {
                tags.add(new TagInfo(TagFormat.ID3V1, offset, ID3V1_SIZE));
            }
        }
        return tags;
    }
}