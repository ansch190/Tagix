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
 * Erkennungsstrategie für AIFF-Metadaten-Chunks.
 * <p>
 * AIFF (Audio Interchange File Format) verwendet die IFF-Chunk-Struktur.
 * Metadaten werden in verschiedenen Chunk-Typen gespeichert:
 * <ul>
 *   <li>NAME: Titel-/Namensinformationen</li>
 *   <li>AUTH: Autor-/Interpretinformationen</li>
 *   <li>ANNO: Anmerkungen/Kommentare</li>
 *   <li>(c)&nbsp;: Urheberrechtsinformationen</li>
 *   <li>COMT: Strukturierte Kommentar-Chunks</li>
 * </ul>
 * <p>
 * Struktur: FORM-Header + Chunk-Typ + Chunks
 */
public class AIFFDetectionStrategy extends TagDetectionStrategy {

    private static final int FORM_HEADER_SIZE = 12;
    private static final int CHUNK_HEADER_SIZE = 8;

    /**
     * {@inheritDoc}
     * <p>
     * Gibt das unterstützte AIFF-Format zurück: AIFF_METADATA.
     */
    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.AIFF_METADATA);
    }

    /**
     * Prüft, ob die Dateidaten ein AIFF/AIFC-Format enthalten, anhand des
     * "FORM"-Kennzeichens und des Typs "AIFF" oder "AIFC".
     *
     * @param startBuffer Puffer mit den ersten Bytes der Datei (mindestens 12 Bytes)
     * @param endBuffer   Puffer mit den letzten Bytes der Datei (nicht verwendet)
     * @return {@code true}, wenn eine AIFF/AIFC-Signatur erkannt wurde
     */
    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        return startBuffer.length >= FORM_HEADER_SIZE && new String(startBuffer, 0, 4, StandardCharsets.US_ASCII).equals("FORM") &&
                (new String(startBuffer, 8, 4, StandardCharsets.US_ASCII).equals("AIFF") || new String(startBuffer, 8, 4, StandardCharsets.US_ASCII).equals("AIFC"));
    }

    /**
     * Analysiert die AIFF-Datei und durchsucht die Chunks nach Metadaten-Typen
     * (NAME, AUTH, ANNO, (c)&nbsp;).
     *
     * @param file        die geöffnete Datei
     * @param filePath    der Dateipfad zur Protokollierung
     * @param startBuffer Puffer mit den ersten Bytes der Datei
     * @param endBuffer   Puffer mit den letzten Bytes der Datei
     * @return eine Liste der erkannten {@link TagInfo}-Objekte
     * @throws IOException wenn ein Fehler beim Lesen der Datei auftritt
     */
    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        if (canDetect(startBuffer, endBuffer)) {
            long position = FORM_HEADER_SIZE;
            long fileLength = file.length();
            while (position + CHUNK_HEADER_SIZE < fileLength) {
                file.seek(position);
                byte[] chunkHeader = new byte[CHUNK_HEADER_SIZE];
                file.read(chunkHeader);
                String chunkType = new String(chunkHeader, 0, 4, StandardCharsets.US_ASCII);
                int chunkSize = ((chunkHeader[4] & 0xFF) << 24) | ((chunkHeader[5] & 0xFF) << 16) |
                        ((chunkHeader[6] & 0xFF) << 8) | (chunkHeader[7] & 0xFF);

                if (chunkSize <= 0) {
                    break;
                }

                if (chunkType.equals("NAME") || chunkType.equals("AUTH") || chunkType.equals("ANNO") || chunkType.equals("(c) ")) {
                    tags.add(new TagInfo(TagFormat.AIFF_METADATA, position, chunkSize + CHUNK_HEADER_SIZE));
                }

                position += CHUNK_HEADER_SIZE + chunkSize;

                if (chunkSize % 2 != 0) {
                    position++;
                }
            }
        }
        return tags;
    }
}