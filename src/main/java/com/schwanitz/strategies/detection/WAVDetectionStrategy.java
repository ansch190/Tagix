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
 * Erkennungsstrategie für WAV-Datei-Metadaten.
 * <p>
 * Unterstützt mehrere Metadatenformate in WAV-Dateien:
 * <ul>
 *   <li>RIFF INFO: Standard-WAV-Metadaten im LIST/INFO-Chunk</li>
 *   <li>BWF (Broadcast Wave Format): Professionelle Audio-Metadaten im bext-Chunk
 *     <ul>
 *       <li>BWF v0: Grundlegende Broadcast-Metadaten</li>
 *       <li>BWF v1: Erweitert um Codierverlauf</li>
 *       <li>BWF v2: Lautheitsinformationen hinzugefügt</li>
 *     </ul>
 *   </li>
 * </ul>
 * <p>
 * WAV-Struktur: RIFF-Header + Chunks (fmt, data, bext, LIST usw.)
 * <p>
 * Verbesserungen:
 * <ul>
 *   <li>Robuste Fehlerbehandlung mit Chunk-Überspringung statt vollständigem Abbruch</li>
 *   <li>Chunk-Typ-Validierung gegen binäre Beschädigung</li>
 *   <li>Erweiterte BWF-Chunk-Validierung</li>
 *   <li>Bessere Protokollierung und Fehlerberichterstattung</li>
 *   <li>Leistungsoptimierungen für große Dateien</li>
 * </ul>
 */
public class WAVDetectionStrategy extends TagDetectionStrategy {

    // Constants for validation and limits
    private static final int MIN_CHUNK_HEADER_SIZE = 8;
    private static final int MAX_REASONABLE_CHUNK_SIZE = 500 * 1024 * 1024; // 500MB
    private static final int BWF_MIN_CHUNK_SIZE = 602;
    private static final int BWF_MAX_REASONABLE_SIZE = 50 * 1024; // 50KB for BWF metadata
    private static final int RIFF_HEADER_SIZE = 12;
    private static final int MAX_CHUNKS_TO_PROCESS = 1000; // Prevent infinite loops

    // BWF version field offset within bext chunk
    private static final int BWF_VERSION_OFFSET = 602;

    /**
     * {@inheritDoc}
     * <p>
     * Gibt die unterstützten WAV-Formate zurück: BWF_V0, BWF_V1, BWF_V2, RIFF_INFO.
     */
    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.BWF_V0, TagFormat.BWF_V1, TagFormat.BWF_V2, TagFormat.RIFF_INFO);
    }

    /**
     * Prüft, ob die Dateidaten eine gültige RIFF/WAVE-Datei enthalten.
     *
     * @param startBuffer Puffer mit den ersten Bytes der Datei (mindestens 12 Bytes)
     * @param endBuffer   Puffer mit den letzten Bytes der Datei (nicht verwendet)
     * @return {@code true}, wenn die RIFF/WAVE-Signatur erkannt wurde
     */
    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (startBuffer.length < RIFF_HEADER_SIZE) {
            return false;
        }
        return isValidRIFFWAVEHeader(startBuffer);
    }

    /**
     * Analysiert die WAV-Datei und durchsucht alle Chunks nach Metadaten.
     * <p>
     * Erkennt LIST/INFO-Chunks (RIFF INFO) und bext-Chunks (BWF) mit
     * Versionsunterscheidung (v0, v1, v2). Enthält robuste Fehlerbehandlung
     * mit Chunk-Wiederherstellung bei Beschädigung.
     *
     * @param file        die geöffnete Datei
     * @param filePath    der Dateipfad zur Protokollierung
     * @param startBuffer Puffer mit den ersten Bytes der Datei
     * @param endBuffer   Puffer mit den letzten Bytes der Datei
     * @return eine Liste der erkannten {@link TagInfo}-Objekte
     * @throws IOException wenn ein Fehler beim Lesen der Datei auftritt
     */
    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath,
                                    byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        if (!canDetect(startBuffer, endBuffer)) {
            return tags;
        }

        LOG.debug("Detecting WAV metadata in file: {}", filePath);

        try {
            long fileLength = file.length();
            long position = RIFF_HEADER_SIZE; // Skip RIFF header (12 bytes)
            int chunksProcessed = 0;
            int corruptedChunksSkipped = 0;

            while (position + MIN_CHUNK_HEADER_SIZE < fileLength &&
                    chunksProcessed < MAX_CHUNKS_TO_PROCESS) {

                ChunkHeader chunkHeader = readChunkHeader(file, position);

                if (chunkHeader == null) {
                    LOG.debug("Failed to read chunk header at position {}, attempting recovery", position);
                    position = attemptChunkRecovery(file, position, fileLength);
                    if (position == -1) {
                        LOG.debug("Could not recover from chunk corruption, ending detection");
                        break;
                    }
                    corruptedChunksSkipped++;
                    continue;
                }

                // Validate a chunk type
                if (!isValidChunkType(chunkHeader.type)) {
                    LOG.debug("Invalid chunk type '{}' at position {}, skipping",
                            chunkHeader.type, position);
                    position += MIN_CHUNK_HEADER_SIZE;
                    corruptedChunksSkipped++;
                    continue;
                }

                // Validate chunk size
                ChunkValidationResult validation = validateChunkSize(chunkHeader.size,
                        position, fileLength);
                if (!validation.isValid) {
                    LOG.warn("Invalid chunk size for '{}' at position {}: {} - {}",
                            chunkHeader.type, position, chunkHeader.size, validation.reason);

                    if (validation.shouldSkip) {
                        position += MIN_CHUNK_HEADER_SIZE;
                        corruptedChunksSkipped++;
                        continue;
                    } else {
                        // Critical error - chunk extends beyond file
                        LOG.debug("Chunk '{}' extends beyond file boundary, ending detection",
                                chunkHeader.type);
                        break;
                    }
                }

                // Process known metadata chunks
                TagInfo tagInfo = processMetadataChunk(file, chunkHeader, position);
                if (tagInfo != null) {
                    tags.add(tagInfo);
                    LOG.debug("Found {} metadata chunk '{}' at offset: {}, size: {} bytes",
                            tagInfo.getFormat().getFormatName(), chunkHeader.type,
                            position, chunkHeader.size + MIN_CHUNK_HEADER_SIZE);
                }

                // Move to the next chunk with proper alignment
                position += MIN_CHUNK_HEADER_SIZE + chunkHeader.size;
                if (chunkHeader.size % 2 != 0) {
                    position++; // RIFF chunks are word-aligned
                }

                chunksProcessed++;
            }

            // Log detection summary
            if (chunksProcessed >= MAX_CHUNKS_TO_PROCESS) {
                LOG.warn("Reached maximum chunk processing limit in {}", filePath);
            }

            if (corruptedChunksSkipped > 0) {
                LOG.info("WAV detection in {} completed: found {} metadata chunks, " +
                        "skipped {} corrupted chunks", filePath, tags.size(), corruptedChunksSkipped);
            } else {
                LOG.debug("WAV detection in {} completed: found {} metadata chunks",
                        filePath, tags.size());
            }

        } catch (IOException e) {
            LOG.error("Error detecting WAV metadata in {}: {}", filePath, e.getMessage());
            throw e;
        }

        return tags;
    }

    /**
     * Validiert den RIFF/WAVE-Header.
     *
     * @param startBuffer Puffer mit den ersten Bytes der Datei
     * @return {@code true}, wenn der Header gültig ist
     */
    private boolean isValidRIFFWAVEHeader(byte[] startBuffer) {
        try {
            String riffSignature = new String(startBuffer, 0, 4, StandardCharsets.US_ASCII);
            String waveType = new String(startBuffer, 8, 4, StandardCharsets.US_ASCII);
            return "RIFF".equals(riffSignature) && "WAVE".equals(waveType);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Liest und validiert einen Chunk-Header an der angegebenen Position.
     *
     * @param file     die geöffnete Datei
     * @param position die Position des Chunk-Headers
     * @return den gelesenen {@link ChunkHeader}, oder {@code null} bei Fehlern
     */
    private ChunkHeader readChunkHeader(RandomAccessFile file, long position) {
        try {
            file.seek(position);
            byte[] headerData = new byte[MIN_CHUNK_HEADER_SIZE];
            int bytesRead = file.read(headerData);

            if (bytesRead != MIN_CHUNK_HEADER_SIZE) {
                LOG.debug("Incomplete chunk header at position {}: read {} bytes",
                        position, bytesRead);
                return null;
            }

            String chunkType = new String(headerData, 0, 4, StandardCharsets.US_ASCII);
            int chunkSize = readLittleEndianInt32(headerData, 4);

            return new ChunkHeader(chunkType, chunkSize);

        } catch (IOException e) {
            LOG.debug("IOException reading chunk header at position {}: {}", position, e.getMessage());
            return null;
        }
    }

    /**
     * Prüft, ob ein Chunk-Typ nur aus druckbaren ASCII-Zeichen und Leerzeichen besteht.
     *
     * @param chunkType der 4-Zeichen-Chunk-Typ
     * @return {@code true}, wenn der Chunk-Typ gültig ist
     */
    private boolean isValidChunkType(String chunkType) {
        if (chunkType == null || chunkType.length() != 4) {
            return false;
        }

        for (char c : chunkType.toCharArray()) {
            // Allow printable ASCII characters and space
            if (c < 0x20 || c > 0x7E) {
                return false;
            }
        }
        return true;
    }

    /**
     * Umfassende Validierung der Chunk-Größe.
     *
     * @param chunkSize  die zu prüfende Chunk-Größe
     * @param position    die Position des Chunks in der Datei
     * @param fileLength  die Gesamtlänge der Datei
     * @return das Validierungsergebnis mit Gültigkeitsstatus und Grund
     */
    private ChunkValidationResult validateChunkSize(int chunkSize, long position, long fileLength) {
        // Check for negative size (corrupted data)
        if (chunkSize < 0) {
            return new ChunkValidationResult(false, true, "negative size");
        }

        // Check for unreasonably large size (likely corruption)
        if (chunkSize > MAX_REASONABLE_CHUNK_SIZE) {
            return new ChunkValidationResult(false, true,
                    String.format("size %d exceeds reasonable limit %d", chunkSize, MAX_REASONABLE_CHUNK_SIZE));
        }

        // Check if chunk extends beyond file
        if (position + MIN_CHUNK_HEADER_SIZE + chunkSize > fileLength) {
            return new ChunkValidationResult(false, false,
                    String.format("chunk extends beyond file (need %d, have %d)",
                            chunkSize, fileLength - position - MIN_CHUNK_HEADER_SIZE));
        }

        return new ChunkValidationResult(true, false, "valid");
    }

    /**
     * Verarbeitet Metadaten-Chunks und erstellt ein {@link TagInfo}.
     *
     * @param file     die geöffnete Datei
     * @param header   der Chunk-Header
     * @param position die Position des Chunks in der Datei
     * @return ein {@link TagInfo} für den erkannten Metadaten-Chunk, oder {@code null}
     * @throws IOException wenn ein Fehler beim Lesen auftritt
     */
    private TagInfo processMetadataChunk(RandomAccessFile file, ChunkHeader header, long position)
            throws IOException {

        switch (header.type) {
            case "LIST":
                return processLISTChunk(file, header, position);
            case "bext":
                return processBEXTChunk(file, header, position);
            default:
                // Not a metadata chunk we're interested in
                return null;
        }
    }

    /**
     * Verarbeitet einen LIST-Chunk zur Erkennung von RIFF-INFO-Metadaten.
     *
     * @param file     die geöffnete Datei
     * @param header   der Chunk-Header
     * @param position die Position des Chunks in der Datei
     * @return ein {@link TagInfo} für den LIST/INFO-Chunk, oder {@code null}
     * @throws IOException wenn ein Fehler beim Lesen auftritt
     */
    private TagInfo processLISTChunk(RandomAccessFile file, ChunkHeader header, long position)
            throws IOException {

        if (header.size < 4) {
            LOG.debug("LIST chunk too small: {} bytes", header.size);
            return null;
        }

        try {
            file.seek(position + MIN_CHUNK_HEADER_SIZE);
            byte[] listType = new byte[4];
            int bytesRead = file.read(listType);

            if (bytesRead != 4) {
                LOG.debug("Could not read LIST type");
                return null;
            }

            String listTypeStr = new String(listType, StandardCharsets.US_ASCII);
            if ("INFO".equals(listTypeStr)) {
                return new TagInfo(TagFormat.RIFF_INFO, position, header.size + MIN_CHUNK_HEADER_SIZE);
            }

        } catch (IOException e) {
            LOG.debug("Error processing LIST chunk: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Verarbeitet einen bext-Chunk zur Erkennung von BWF-Metadaten mit erweiterter Validierung.
     *
     * @param file     die geöffnete Datei
     * @param header   der Chunk-Header
     * @param position die Position des Chunks in der Datei
     * @return ein {@link TagInfo} für den bext-Chunk mit entsprechender BWF-Version, oder {@code null}
     * @throws IOException wenn ein Fehler beim Lesen auftritt
     */
    private TagInfo processBEXTChunk(RandomAccessFile file, ChunkHeader header, long position)
            throws IOException {

        // Validate BWF chunk size
        if (header.size < BWF_MIN_CHUNK_SIZE) {
            LOG.debug("bext chunk too small for BWF: {} bytes (minimum: {})",
                    header.size, BWF_MIN_CHUNK_SIZE);
            return null;
        }

        if (header.size > BWF_MAX_REASONABLE_SIZE) {
            LOG.warn("bext chunk unusually large: {} bytes, processing anyway", header.size);
        }

        try {
            // Read BWF version field
            file.seek(position + MIN_CHUNK_HEADER_SIZE + BWF_VERSION_OFFSET);
            byte[] versionBuffer = new byte[2];
            int bytesRead = file.read(versionBuffer);

            if (bytesRead != 2) {
                LOG.debug("Could not read BWF version field");
                return null;
            }

            int version = readLittleEndianInt16(versionBuffer, 0);
            TagFormat format = mapBWFVersion(version);

            if (format != null) {
                return new TagInfo(format, position, header.size + MIN_CHUNK_HEADER_SIZE);
            } else {
                LOG.debug("Unknown BWF version: {}", version);
                // Still return a generic BWF format for unknown versions
                return new TagInfo(TagFormat.BWF_V2, position, header.size + MIN_CHUNK_HEADER_SIZE);
            }

        } catch (IOException e) {
            LOG.debug("Error processing bext chunk: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Ordnet eine BWF-Versionsnummer dem entsprechenden {@link TagFormat} zu.
     *
     * @param version die BWF-Versionsnummer (0, 1 oder 2)
     * @return das entsprechende TagFormat, oder {@code null} bei unbekannter Version
     */
    private TagFormat mapBWFVersion(int version) {
        return switch (version) {
            case 0 -> TagFormat.BWF_V0;
            case 1 -> TagFormat.BWF_V1;
            case 2 -> TagFormat.BWF_V2;
            default -> {
                LOG.debug("Unknown BWF version: {}", version);
                yield null;
            }
        };
    }

    /**
     * Versucht, die Erkennung nach einer Chunk-Beschädigung fortzusetzen,
     * indem nach dem nächsten gültigen Chunk gesucht wird.
     *
     * @param file         die geöffnete Datei
     * @param startPosition die Startposition für die Suche
     * @param fileLength    die Gesamtlänge der Datei
     * @return die Position des nächsten gültigen Chunks, oder -1 wenn kein solcher gefunden wurde
     * @throws IOException wenn ein Fehler beim Lesen auftritt
     */
    private long attemptChunkRecovery(RandomAccessFile file, long startPosition, long fileLength)
            throws IOException {

        LOG.debug("Attempting chunk recovery from position {}", startPosition);

        long searchPosition = startPosition + 1;
        long maxSearchDistance = Math.min(1024, fileLength - searchPosition); // Search up to 1KB

        while (searchPosition + MIN_CHUNK_HEADER_SIZE < startPosition + maxSearchDistance) {
            try {
                file.seek(searchPosition);
                byte[] candidateHeader = new byte[4];
                int bytesRead = file.read(candidateHeader);

                if (bytesRead == 4) {
                    String candidateType = new String(candidateHeader, StandardCharsets.US_ASCII);
                    if (isValidChunkType(candidateType) && isKnownChunkType(candidateType)) {
                        LOG.debug("Found potential chunk '{}' at position {} during recovery",
                                candidateType, searchPosition);
                        return searchPosition;
                    }
                }

            } catch (IOException e) {
                // Continue searching
            }

            searchPosition++;
        }

        LOG.debug("Chunk recovery failed - no valid chunk found within search distance");
        return -1;
    }

    /**
     * Prüft, ob ein Chunk-Typ ein bekannter RIFF-Chunk-Typ ist.
     *
     * @param chunkType der 4-Zeichen-Chunk-Typ
     * @return {@code true}, wenn der Chunk-Typ bekannt ist
     */
    private boolean isKnownChunkType(String chunkType) {
        return switch (chunkType) {
            case "fmt ", "data", "LIST", "bext", "fact", "cue ", "plst", "ltxt", "note", "labl" -> true;
            default -> false;
        };
    }

    /**
     * Liest einen 32-Bit-Little-Endian-Integer aus einem Byte-Array.
     *
     * @param data   das Byte-Array
     * @param offset der Offset im Array
     * @return der gelesene Integer-Wert
     */
    private int readLittleEndianInt32(byte[] data, int offset) {
        return ((data[offset] & 0xFF)) |
                ((data[offset + 1] & 0xFF) << 8) |
                ((data[offset + 2] & 0xFF) << 16) |
                ((data[offset + 3] & 0xFF) << 24);
    }

    /**
     * Liest einen 16-Bit-Little-Endian-Integer aus einem Byte-Array.
     *
     * @param data   das Byte-Array
     * @param offset der Offset im Array
     * @return der gelesene Integer-Wert
     */
    private int readLittleEndianInt16(byte[] data, int offset) {
        return ((data[offset] & 0xFF)) |
                ((data[offset + 1] & 0xFF) << 8);
    }

    /**
 * Datenklasse für Chunk-Header-Informationen.
 *
 * @param type der 4-Zeichen-Chunk-Typ
 * @param size die Chunk-Größe in Bytes (ohne Header)
 */
    private static class ChunkHeader {
        final String type;
        final int size;

        ChunkHeader(String type, int size) {
            this.type = type;
            this.size = size;
        }

        @Override
        public String toString() {
            return String.format("ChunkHeader{type='%s', size=%d}", type, size);
        }
    }

    /**
 * Datenklasse für Chunk-Validierungsergebnisse.
 *
 * @param isValid     ob die Chunk-Größe gültig ist
 * @param shouldSkip  ob der Chunk übersprungen werden soll (true) oder die Erkennung abgebrochen werden soll (false)
 * @param reason      der Grund für die Validierungsbewertung
 */
    private static class ChunkValidationResult {
        final boolean isValid;
        final boolean shouldSkip; // true = skip chunk, false = abort detection
        final String reason;

        ChunkValidationResult(boolean isValid, boolean shouldSkip, String reason) {
            this.isValid = isValid;
            this.shouldSkip = shouldSkip;
            this.reason = reason;
        }
    }
}