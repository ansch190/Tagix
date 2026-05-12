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
 * Erkennungsstrategie für ID3v2-Tags (Versionen 2.2, 2.3 und 2.4).
 * <p>
 * ID3v2-Tags befinden sich am Anfang von Audiodateien und haben folgende Struktur:
 * <ul>
 *   <li>Kennzeichen: "ID3" (3 Bytes)</li>
 *   <li>Hauptversion: 1 Byte (2, 3 oder 4)</li>
 *   <li>Nebenversion: 1 Byte (immer 0)</li>
 *   <li>Flags: 1 Byte</li>
 *   <li>Größe: 4 Bytes (Synchsafe-Integer in allen Versionen)</li>
 * </ul>
 * <p>
 * Das Tag-Größenfeld ist in allen ID3v2-Versionen als Synchsafe-Integer kodiert.
 * Jedes Byte nutzt nur 7 Bits (MSB ist immer 0), was eine maximale Tag-Größe
 * von 256 MB ergibt.
 * <p>
 * Versionsunterschiede betreffen die Frame-Struktur:
 * <ul>
 *   <li>ID3v2.2: 3-Zeichen-Frame-IDs, 24-Bit-Frame-Größen ohne Synchsafe</li>
 *   <li>ID3v2.3: 4-Zeichen-Frame-IDs, 32-Bit-Frame-Größen ohne Synchsafe</li>
 *   <li>ID3v2.4: 4-Zeichen-Frame-IDs, Synchsafe-Frame-Größen, UTF-8-Unterstützung</li>
 * </ul>
 */
public class ID3V2DetectionStrategy extends TagDetectionStrategy {

    private static final int ID3V2_HEADER_SIZE = 10;
    private static final int ID3V2_SIGNATURE_LENGTH = 3;
    private static final int SYNCHSAFE_MASK = 0x7F;

    /**
     * {@inheritDoc}
     * <p>
     * Gibt die unterstützten ID3v2-Formate zurück: ID3V2_2, ID3V2_3, ID3V2_4.
     */
    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.ID3V2_2, TagFormat.ID3V2_3, TagFormat.ID3V2_4);
    }

    /**
     * Prüft, ob die Dateidaten ein ID3v2-Tag enthalten, anhand des "ID3"-Kennzeichens
     * am Dateianfang.
     *
     * @param startBuffer Puffer mit den ersten Bytes der Datei (mindestens 10 Bytes)
     * @param endBuffer   Puffer mit den letzten Bytes der Datei (nicht verwendet)
     * @return {@code true}, wenn das ID3v2-Kennzeichen gefunden wurde
     */
    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (startBuffer.length < ID3V2_HEADER_SIZE) {
            return false;
        }
        return new String(startBuffer, 0, ID3V2_SIGNATURE_LENGTH, StandardCharsets.US_ASCII).equals("ID3");
    }

    /**
     * Analysiert das ID3v2-Tag und ermittelt die genaue Version, Position und Größe.
     * <p>
     * Liest den Header, dekodiert die Tag-Größe als Synchsafe-Integer und ordnet
     * die Hauptversion dem entsprechenden {@link TagFormat} zu.
     *
     * @param file        die geöffnete Datei
     * @param filePath    der Dateipfad zur Protokollierung
     * @param startBuffer Puffer mit den ersten Bytes der Datei
     * @param endBuffer   Puffer mit den letzten Bytes der Datei
     * @return eine Liste mit maximal einem {@link TagInfo}-Objekt für das erkannte ID3v2-Tag
     * @throws IOException wenn ein Fehler beim Lesen der Datei auftritt
     */
    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        if (canDetect(startBuffer, endBuffer)) {
            int majorVersion = startBuffer[3] & 0xFF;
            int revision = startBuffer[4] & 0xFF;
            if (revision != 0) {
                LOG.debug("Invalid Revision for ID3v2: {}", revision);
                return tags;
            }

            int tagSize = decodeSynchsafeInt(startBuffer[6], startBuffer[7], startBuffer[8], startBuffer[9]);

            TagFormat format = switch (majorVersion) {
                case 2 -> TagFormat.ID3V2_2;
                case 3 -> TagFormat.ID3V2_3;
                case 4 -> TagFormat.ID3V2_4;
                default -> {
                    LOG.debug("Unknown ID3v2-Version: {}", majorVersion);
                    yield null;
                }
            };

            if (format != null) {
                tags.add(new TagInfo(format, 0, tagSize + ID3V2_HEADER_SIZE));
            }
        }
        return tags;
    }

    /**
     * Dekodiert ein 4-Byte-Synchsafe-Integer, wie es in ID3v2-Tag-Headern verwendet wird.
     * <p>
     * Jedes Byte nutzt nur 7 Bits (MSB ist immer 0).
     * Maximaler Wert: 2^28 - 1 = 268435455 (256 MB - 1).
     *
     * @param b0 höchstwertiges Byte
     * @param b1 zweithöchstes Byte
     * @param b2 dritthöchstes Byte
     * @param b3 niedrigstwertiges Byte
     * @return der dekodierte Integer-Wert
     */
    private int decodeSynchsafeInt(byte b0, byte b1, byte b2, byte b3) {
        return ((b0 & SYNCHSAFE_MASK) << 21) |
                ((b1 & SYNCHSAFE_MASK) << 14) |
                ((b2 & SYNCHSAFE_MASK) << 7) |
                (b3 & SYNCHSAFE_MASK);
    }
}