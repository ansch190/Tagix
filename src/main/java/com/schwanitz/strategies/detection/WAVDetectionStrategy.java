package com.schwanitz.strategies.detection;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private static final int MIN_CHUNK_HEADER_SIZE = 8;
    private static final int MAX_REASONABLE_CHUNK_SIZE = 500 * 1024 * 1024;
    private static final int BWF_MIN_CHUNK_SIZE = 602;
    private static final int BWF_MAX_REASONABLE_SIZE = 50 * 1024;
    private static final int RIFF_HEADER_SIZE = 12;
    private static final int MAX_CHUNKS_TO_PROCESS = 1000;
    private static final int BWF_VERSION_OFFSET = 602;

    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.BWF_V0, TagFormat.BWF_V1, TagFormat.BWF_V2, TagFormat.RIFF_INFO);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (startBuffer.length < RIFF_HEADER_SIZE) {
            return false;
        }
        return isValidRIFFWAVEHeader(startBuffer);
    }

    @Override
    public List<TagInfo> detectTags(SeekableDataSource source, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        if (!canDetect(startBuffer, endBuffer)) {
            return tags;
        }

        LOG.debug("Detecting WAV metadata in source: {}", source.name());

        try {
            long fileLength = source.length();
            long position = RIFF_HEADER_SIZE;
            int chunksProcessed = 0;
            int corruptedChunksSkipped = 0;

            while (position + MIN_CHUNK_HEADER_SIZE < fileLength &&
                    chunksProcessed < MAX_CHUNKS_TO_PROCESS) {

                ChunkHeader chunkHeader = readChunkHeader(source, position);

                if (chunkHeader == null) {
                    LOG.debug("Failed to read chunk header at position {}, attempting recovery", position);
                    position = attemptChunkRecovery(source, position, fileLength);
                    if (position == -1) {
                        LOG.debug("Could not recover from chunk corruption, ending detection");
                        break;
                    }
                    corruptedChunksSkipped++;
                    continue;
                }

                if (!isValidChunkType(chunkHeader.type)) {
                    LOG.debug("Invalid chunk type '{}' at position {}, skipping",
                            chunkHeader.type, position);
                    position += MIN_CHUNK_HEADER_SIZE;
                    corruptedChunksSkipped++;
                    continue;
                }

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
                        LOG.debug("Chunk '{}' extends beyond file boundary, ending detection",
                                chunkHeader.type);
                        break;
                    }
                }

                TagInfo tagInfo = processMetadataChunk(source, chunkHeader, position);
                if (tagInfo != null) {
                    tags.add(tagInfo);
                    LOG.debug("Found {} metadata chunk '{}' at offset: {}, size: {} bytes",
                            tagInfo.getFormat().getFormatName(), chunkHeader.type,
                            position, chunkHeader.size + MIN_CHUNK_HEADER_SIZE);
                }

                position += MIN_CHUNK_HEADER_SIZE + chunkHeader.size;
                if (chunkHeader.size % 2 != 0) {
                    position++;
                }

                chunksProcessed++;
            }

            if (chunksProcessed >= MAX_CHUNKS_TO_PROCESS) {
                LOG.warn("Reached maximum chunk processing limit in {}", source.name());
            }

            if (corruptedChunksSkipped > 0) {
                LOG.info("WAV detection in {} completed: found {} metadata chunks, " +
                        "skipped {} corrupted chunks", source.name(), tags.size(), corruptedChunksSkipped);
            } else {
                LOG.debug("WAV detection in {} completed: found {} metadata chunks",
                        source.name(), tags.size());
            }

        } catch (IOException e) {
            LOG.error("Error detecting WAV metadata in {}: {}", source.name(), e.getMessage());
            throw e;
        }

        return tags;
    }

    private boolean isValidRIFFWAVEHeader(byte[] startBuffer) {
        try {
            String riffSignature = new String(startBuffer, 0, 4, StandardCharsets.US_ASCII);
            String waveType = new String(startBuffer, 8, 4, StandardCharsets.US_ASCII);
            return "RIFF".equals(riffSignature) && "WAVE".equals(waveType);
        } catch (Exception e) {
            return false;
        }
    }

    private ChunkHeader readChunkHeader(SeekableDataSource source, long position) {
        try {
            byte[] headerData = new byte[MIN_CHUNK_HEADER_SIZE];
            int bytesRead = source.read(position, headerData, 0, MIN_CHUNK_HEADER_SIZE);

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

    private boolean isValidChunkType(String chunkType) {
        if (chunkType == null || chunkType.length() != 4) {
            return false;
        }

        for (char c : chunkType.toCharArray()) {
            if (c < 0x20 || c > 0x7E) {
                return false;
            }
        }
        return true;
    }

    private ChunkValidationResult validateChunkSize(int chunkSize, long position, long fileLength) {
        if (chunkSize < 0) {
            return new ChunkValidationResult(false, true, "negative size");
        }

        if (chunkSize > MAX_REASONABLE_CHUNK_SIZE) {
            return new ChunkValidationResult(false, true,
                    String.format("size %d exceeds reasonable limit %d", chunkSize, MAX_REASONABLE_CHUNK_SIZE));
        }

        if (position + MIN_CHUNK_HEADER_SIZE + chunkSize > fileLength) {
            return new ChunkValidationResult(false, false,
                    String.format("chunk extends beyond file (need %d, have %d)",
                            chunkSize, fileLength - position - MIN_CHUNK_HEADER_SIZE));
        }

        return new ChunkValidationResult(true, false, "valid");
    }

    private TagInfo processMetadataChunk(SeekableDataSource source, ChunkHeader header, long position)
            throws IOException {

        switch (header.type) {
            case "LIST":
                return processLISTChunk(source, header, position);
            case "bext":
                return processBEXTChunk(source, header, position);
            default:
                return null;
        }
    }

    private TagInfo processLISTChunk(SeekableDataSource source, ChunkHeader header, long position)
            throws IOException {

        if (header.size < 4) {
            LOG.debug("LIST chunk too small: {} bytes", header.size);
            return null;
        }

        try {
            byte[] listType = new byte[4];
            int bytesRead = source.read(position + MIN_CHUNK_HEADER_SIZE, listType, 0, 4);

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

    private TagInfo processBEXTChunk(SeekableDataSource source, ChunkHeader header, long position)
            throws IOException {

        if (header.size < BWF_MIN_CHUNK_SIZE) {
            LOG.debug("bext chunk too small for BWF: {} bytes (minimum: {})",
                    header.size, BWF_MIN_CHUNK_SIZE);
            return null;
        }

        if (header.size > BWF_MAX_REASONABLE_SIZE) {
            LOG.warn("bext chunk unusually large: {} bytes, processing anyway", header.size);
        }

        try {
            byte[] versionBuffer = new byte[2];
            int bytesRead = source.read(position + MIN_CHUNK_HEADER_SIZE + BWF_VERSION_OFFSET, versionBuffer, 0, 2);

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
                return new TagInfo(TagFormat.BWF_V2, position, header.size + MIN_CHUNK_HEADER_SIZE);
            }

        } catch (IOException e) {
            LOG.debug("Error processing bext chunk: {}", e.getMessage());
            return null;
        }
    }

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

    private long attemptChunkRecovery(SeekableDataSource source, long startPosition, long fileLength)
            throws IOException {

        LOG.debug("Attempting chunk recovery from position {}", startPosition);

        long searchPosition = startPosition + 1;
        long maxSearchDistance = Math.min(1024, fileLength - searchPosition);

        while (searchPosition + MIN_CHUNK_HEADER_SIZE < startPosition + maxSearchDistance) {
            try {
                byte[] candidateHeader = new byte[4];
                int bytesRead = source.read(searchPosition, candidateHeader, 0, 4);

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

    private boolean isKnownChunkType(String chunkType) {
        return switch (chunkType) {
            case "fmt ", "data", "LIST", "bext", "fact", "cue ", "plst", "ltxt", "note", "labl" -> true;
            default -> false;
        };
    }

    private int readLittleEndianInt32(byte[] data, int offset) {
        return ((data[offset] & 0xFF)) |
                ((data[offset + 1] & 0xFF) << 8) |
                ((data[offset + 2] & 0xFF) << 16) |
                ((data[offset + 3] & 0xFF) << 24);
    }

    private int readLittleEndianInt16(byte[] data, int offset) {
        return ((data[offset] & 0xFF)) |
                ((data[offset + 1] & 0xFF) << 8);
    }

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

    private static class ChunkValidationResult {
        final boolean isValid;
        final boolean shouldSkip;
        final String reason;

        ChunkValidationResult(boolean isValid, boolean shouldSkip, String reason) {
            this.isValid = isValid;
            this.shouldSkip = shouldSkip;
            this.reason = reason;
        }
    }
}