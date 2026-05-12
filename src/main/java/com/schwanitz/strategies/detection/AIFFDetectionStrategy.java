package com.schwanitz.strategies.detection;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
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

    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.AIFF_METADATA);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        return startBuffer.length >= FORM_HEADER_SIZE && new String(startBuffer, 0, 4, StandardCharsets.US_ASCII).equals("FORM") &&
                (new String(startBuffer, 8, 4, StandardCharsets.US_ASCII).equals("AIFF") || new String(startBuffer, 8, 4, StandardCharsets.US_ASCII).equals("AIFC"));
    }

    @Override
    public List<TagInfo> detectTags(SeekableDataSource source, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        if (canDetect(startBuffer, endBuffer)) {
            long position = FORM_HEADER_SIZE;
            long sourceLength = source.length();
            while (position + CHUNK_HEADER_SIZE < sourceLength) {
                byte[] chunkHeader = new byte[CHUNK_HEADER_SIZE];
                int bytesRead = source.read(position, chunkHeader, 0, CHUNK_HEADER_SIZE);
                if (bytesRead != CHUNK_HEADER_SIZE) break;

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