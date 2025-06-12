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
 * Detection Strategy for WAV file metadata
 * <p>
 * Supports multiple metadata formats in WAV files:
 * - RIFF INFO: Standard WAV metadata in LIST/INFO chunk
 * - BWF (Broadcast Wave Format): Professional audio metadata in bext chunk
 *   - BWF v0: Basic broadcast metadata
 *   - BWF v1: Extended with coding history
 *   - BWF v2: Loudness information added
 * <p>
 * WAV structure: RIFF header + chunks (fmt, data, bext, LIST, etc.)
 * <p>
 * Improvements:
 * - Robust error handling with chunk skipping instead of complete abort
 * - Chunk type validation against binary corruption
 * - Enhanced BWF chunk validation
 * - Better logging and error reporting
 * - Performance optimizations for large files
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
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath,
                                    byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        if (!canDetect(startBuffer, endBuffer)) {
            return tags;
        }

        Log.debug("Detecting WAV metadata in file: {}", filePath);

        try {
            long fileLength = file.length();
            long position = RIFF_HEADER_SIZE; // Skip RIFF header (12 bytes)
            int chunksProcessed = 0;
            int corruptedChunksSkipped = 0;

            while (position + MIN_CHUNK_HEADER_SIZE < fileLength &&
                    chunksProcessed < MAX_CHUNKS_TO_PROCESS) {

                ChunkHeader chunkHeader = readChunkHeader(file, position);

                if (chunkHeader == null) {
                    Log.debug("Failed to read chunk header at position {}, attempting recovery", position);
                    position = attemptChunkRecovery(file, position, fileLength);
                    if (position == -1) {
                        Log.debug("Could not recover from chunk corruption, ending detection");
                        break;
                    }
                    corruptedChunksSkipped++;
                    continue;
                }

                // Validate a chunk type
                if (!isValidChunkType(chunkHeader.type)) {
                    Log.debug("Invalid chunk type '{}' at position {}, skipping",
                            chunkHeader.type, position);
                    position += MIN_CHUNK_HEADER_SIZE;
                    corruptedChunksSkipped++;
                    continue;
                }

                // Validate chunk size
                ChunkValidationResult validation = validateChunkSize(chunkHeader.size,
                        position, fileLength);
                if (!validation.isValid) {
                    Log.warn("Invalid chunk size for '{}' at position {}: {} - {}",
                            chunkHeader.type, position, chunkHeader.size, validation.reason);

                    if (validation.shouldSkip) {
                        position += MIN_CHUNK_HEADER_SIZE;
                        corruptedChunksSkipped++;
                        continue;
                    } else {
                        // Critical error - chunk extends beyond file
                        Log.debug("Chunk '{}' extends beyond file boundary, ending detection",
                                chunkHeader.type);
                        break;
                    }
                }

                // Process known metadata chunks
                TagInfo tagInfo = processMetadataChunk(file, chunkHeader, position);
                if (tagInfo != null) {
                    tags.add(tagInfo);
                    Log.debug("Found {} metadata chunk '{}' at offset: {}, size: {} bytes",
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
                Log.warn("Reached maximum chunk processing limit in {}", filePath);
            }

            if (corruptedChunksSkipped > 0) {
                Log.info("WAV detection in {} completed: found {} metadata chunks, " +
                        "skipped {} corrupted chunks", filePath, tags.size(), corruptedChunksSkipped);
            } else {
                Log.debug("WAV detection in {} completed: found {} metadata chunks",
                        filePath, tags.size());
            }

        } catch (IOException e) {
            Log.error("Error detecting WAV metadata in {}: {}", filePath, e.getMessage());
            throw e;
        }

        return tags;
    }

    /**
     * Validate RIFF/WAVE header
     */
    private boolean isValidRIFFWAVEHeader(byte[] startBuffer) {
        try {
            String riffSignature = new String(startBuffer, 0, 4, "ASCII");
            String waveType = new String(startBuffer, 8, 4, "ASCII");
            return "RIFF".equals(riffSignature) && "WAVE".equals(waveType);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Read and validate chunk header
     */
    private ChunkHeader readChunkHeader(RandomAccessFile file, long position) {
        try {
            file.seek(position);
            byte[] headerData = new byte[MIN_CHUNK_HEADER_SIZE];
            int bytesRead = file.read(headerData);

            if (bytesRead != MIN_CHUNK_HEADER_SIZE) {
                Log.debug("Incomplete chunk header at position {}: read {} bytes",
                        position, bytesRead);
                return null;
            }

            String chunkType = new String(headerData, 0, 4, "ASCII");
            int chunkSize = readLittleEndianInt32(headerData, 4);

            return new ChunkHeader(chunkType, chunkSize);

        } catch (IOException e) {
            Log.debug("IOException reading chunk header at position {}: {}", position, e.getMessage());
            return null;
        }
    }

    /**
     * Validate a chunk type for ASCII printable characters
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
     * Comprehensive chunk size validation
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
     * Process metadata chunks and create TagInfo
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
     * Process LIST chunk for RIFF INFO metadata
     */
    private TagInfo processLISTChunk(RandomAccessFile file, ChunkHeader header, long position)
            throws IOException {

        if (header.size < 4) {
            Log.debug("LIST chunk too small: {} bytes", header.size);
            return null;
        }

        try {
            file.seek(position + MIN_CHUNK_HEADER_SIZE);
            byte[] listType = new byte[4];
            int bytesRead = file.read(listType);

            if (bytesRead != 4) {
                Log.debug("Could not read LIST type");
                return null;
            }

            String listTypeStr = new String(listType, "ASCII");
            if ("INFO".equals(listTypeStr)) {
                return new TagInfo(TagFormat.RIFF_INFO, position, header.size + MIN_CHUNK_HEADER_SIZE);
            }

        } catch (IOException e) {
            Log.debug("Error processing LIST chunk: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Process bext chunk for BWF metadata with enhanced validation
     */
    private TagInfo processBEXTChunk(RandomAccessFile file, ChunkHeader header, long position)
            throws IOException {

        // Validate BWF chunk size
        if (header.size < BWF_MIN_CHUNK_SIZE) {
            Log.debug("bext chunk too small for BWF: {} bytes (minimum: {})",
                    header.size, BWF_MIN_CHUNK_SIZE);
            return null;
        }

        if (header.size > BWF_MAX_REASONABLE_SIZE) {
            Log.warn("bext chunk unusually large: {} bytes, processing anyway", header.size);
        }

        try {
            // Read BWF version field
            file.seek(position + MIN_CHUNK_HEADER_SIZE + BWF_VERSION_OFFSET);
            byte[] versionBuffer = new byte[2];
            int bytesRead = file.read(versionBuffer);

            if (bytesRead != 2) {
                Log.debug("Could not read BWF version field");
                return null;
            }

            int version = readLittleEndianInt16(versionBuffer, 0);
            TagFormat format = mapBWFVersion(version);

            if (format != null) {
                return new TagInfo(format, position, header.size + MIN_CHUNK_HEADER_SIZE);
            } else {
                Log.debug("Unknown BWF version: {}", version);
                // Still return a generic BWF format for unknown versions
                return new TagInfo(TagFormat.BWF_V2, position, header.size + MIN_CHUNK_HEADER_SIZE);
            }

        } catch (IOException e) {
            Log.debug("Error processing bext chunk: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Map BWF version number to TagFormat
     */
    private TagFormat mapBWFVersion(int version) {
        return switch (version) {
            case 0 -> TagFormat.BWF_V0;
            case 1 -> TagFormat.BWF_V1;
            case 2 -> TagFormat.BWF_V2;
            default -> {
                Log.debug("Unknown BWF version: {}", version);
                yield null;
            }
        };
    }

    /**
     * Attempt to recover from chunk corruption by searching for the next valid chunk
     */
    private long attemptChunkRecovery(RandomAccessFile file, long startPosition, long fileLength)
            throws IOException {

        Log.debug("Attempting chunk recovery from position {}", startPosition);

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
                        Log.debug("Found potential chunk '{}' at position {} during recovery",
                                candidateType, searchPosition);
                        return searchPosition;
                    }
                }

            } catch (IOException e) {
                // Continue searching
            }

            searchPosition++;
        }

        Log.debug("Chunk recovery failed - no valid chunk found within search distance");
        return -1;
    }

    /**
     * Check if chunk type is a known RIFF chunk type
     */
    private boolean isKnownChunkType(String chunkType) {
        return switch (chunkType) {
            case "fmt ", "data", "LIST", "bext", "fact", "cue ", "plst", "ltxt", "note", "labl" -> true;
            default -> false;
        };
    }

    /**
     * Read 32-bit little-endian integer from a byte array
     */
    private int readLittleEndianInt32(byte[] data, int offset) {
        return ((data[offset] & 0xFF)) |
                ((data[offset + 1] & 0xFF) << 8) |
                ((data[offset + 2] & 0xFF) << 16) |
                ((data[offset + 3] & 0xFF) << 24);
    }

    /**
     * Read 16-bit little-endian integer from a byte array
     */
    private int readLittleEndianInt16(byte[] data, int offset) {
        return ((data[offset] & 0xFF)) |
                ((data[offset + 1] & 0xFF) << 8);
    }

    /**
     * Data class for chunk header information
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
     * Data class for chunk validation results
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