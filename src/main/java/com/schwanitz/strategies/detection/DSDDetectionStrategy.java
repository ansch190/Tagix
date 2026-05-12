package com.schwanitz.strategies.detection;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Erkennungsstrategie für DSD-Formate (Direct Stream Digital).
 * <p>
 * Unterstützt zwei DSD-Containerformate:
 * <ol>
 *   <li><b>DSF</b> (DSD Stream File):
 *       <ul>
 *         <li>Kennzeichen: "DSD " (0x44534420)</li>
 *         <li>Chunk-basierte Struktur ähnlich WAV/RIFF</li>
 *         <li>Metadaten im "ID3 "-Chunk (ID3v2-Tags)</li>
 *       </ul>
 *   </li>
 *   <li><b>DFF</b> (DSDIFF - DSD Interchange File Format):
 *       <ul>
 *         <li>Kennzeichen: "FRM8" + "DSD " (AIFF-ähnlich)</li>
 *         <li>Chunk-basierte Struktur</li>
 *         <li>Metadaten in verschiedenen Chunks (DIIN, DITI, ID3 usw.)</li>
 *       </ul>
 *   </li>
 * </ol>
 */
public class DSDDetectionStrategy extends TagDetectionStrategy {

    private static final byte[] DSF_SIGNATURE = {'D', 'S', 'D', ' '};
    private static final byte[] DSF_ID3_CHUNK = {'I', 'D', '3', ' '};

    private static final byte[] DFF_FORM_SIGNATURE = {'F', 'R', 'M', '8'};
    private static final byte[] DFF_DSD_TYPE = {'D', 'S', 'D', ' '};

    private static final byte[] DFF_DIIN_CHUNK = {'D', 'I', 'I', 'N'};
    private static final byte[] DFF_DITI_CHUNK = {'D', 'I', 'T', 'I'};
    private static final byte[] DFF_ID3_CHUNK = {'I', 'D', '3', ' '};

    private static final int DSF_METADATA_POINTER_OFFSET = 20;
    private static final int DSF_ID3_CHUNK_HEADER_SIZE = 12;

    private static final int DFF_FORM_HEADER_SIZE = 16;
    private static final int DFF_CHUNK_HEADER_SIZE = 12;
    private static final int DFF_MIN_TYPE_LENGTH = 8;

    private static final int DSF_MIN_BUFFER = 8;
    private static final int DFF_MIN_BUFFER = 12;

    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.DSF_METADATA, TagFormat.DFF_METADATA);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (startBuffer.length < DSF_MIN_BUFFER) {
            return false;
        }

        if (Arrays.equals(Arrays.copyOfRange(startBuffer, 0, 4), DSF_SIGNATURE)) {
            return true;
        }

        if (Arrays.equals(Arrays.copyOfRange(startBuffer, 0, 4), DFF_FORM_SIGNATURE) &&
                startBuffer.length >= DFF_MIN_BUFFER &&
                Arrays.equals(Arrays.copyOfRange(startBuffer, 8, 12), DFF_DSD_TYPE)) {
            return true;
        }

        return false;
    }

    @Override
    public List<TagInfo> detectTags(SeekableDataSource source, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        if (!canDetect(startBuffer, endBuffer)) {
            return tags;
        }

        LOG.debug("Detecting DSD tags in source: {}", source.name());

        if (Arrays.equals(Arrays.copyOfRange(startBuffer, 0, 4), DSF_SIGNATURE)) {
            tags.addAll(detectDSFTags(source));
        } else if (Arrays.equals(Arrays.copyOfRange(startBuffer, 0, 4), DFF_FORM_SIGNATURE)) {
            tags.addAll(detectDFFTags(source));
        }

        return tags;
    }

    private List<TagInfo> detectDSFTags(SeekableDataSource source) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        try {
            byte[] metadataPointerBytes = new byte[8];
            source.read(DSF_METADATA_POINTER_OFFSET, metadataPointerBytes, 0, 8);
            long metadataPointer = readLittleEndianLong(metadataPointerBytes, 0);

            if (metadataPointer > 0 && metadataPointer < source.length()) {
                byte[] chunkId = new byte[4];
                source.read(metadataPointer, chunkId, 0, 4);

                if (Arrays.equals(chunkId, DSF_ID3_CHUNK)) {
                    byte[] sizeBytes = new byte[8];
                    source.read(metadataPointer + 4, sizeBytes, 0, 8);
                    long id3ChunkSize = readLittleEndianLong(sizeBytes, 0);

                    if (id3ChunkSize > 0 && id3ChunkSize < source.length() - metadataPointer) {
                        tags.add(new TagInfo(TagFormat.DSF_METADATA, metadataPointer, id3ChunkSize + DSF_ID3_CHUNK_HEADER_SIZE));
                        LOG.debug("Found DSF ID3 metadata at offset: {}, size: {} bytes",
                                metadataPointer, id3ChunkSize + DSF_ID3_CHUNK_HEADER_SIZE);
                    }
                }
            }

        } catch (IOException e) {
            LOG.error("Error detecting DSF tags in {}: {}", source.name(), e.getMessage());
            throw e;
        }

        return tags;
    }

    private List<TagInfo> detectDFFTags(SeekableDataSource source) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        try {
            byte[] chunkId = new byte[4];
            source.read(0, chunkId, 0, 4);

            if (!Arrays.equals(chunkId, DFF_FORM_SIGNATURE)) {
                return tags;
            }

            byte[] formSizeBytes = new byte[8];
            source.read(4, formSizeBytes, 0, 8);
            long formSize = readBigEndianLong(formSizeBytes, 0);

            byte[] dsdType = new byte[4];
            source.read(12, dsdType, 0, 4);

            if (!Arrays.equals(dsdType, DFF_DSD_TYPE)) {
                return tags;
            }

            long currentPos = DFF_FORM_HEADER_SIZE;
            long endPos = DFF_CHUNK_HEADER_SIZE + formSize;

            while (currentPos + DFF_CHUNK_HEADER_SIZE < endPos) {
                byte[] dffChunkId = new byte[4];
                source.read(currentPos, dffChunkId, 0, 4);

                byte[] chunkSizeBytes = new byte[8];
                source.read(currentPos + 4, chunkSizeBytes, 0, 8);
                long chunkSize = readBigEndianLong(chunkSizeBytes, 0);

                if (chunkSize < 0 || chunkSize > endPos - currentPos - DFF_CHUNK_HEADER_SIZE) {
                    break;
                }

                if (Arrays.equals(dffChunkId, DFF_DIIN_CHUNK) ||
                        Arrays.equals(dffChunkId, DFF_DITI_CHUNK) ||
                        Arrays.equals(dffChunkId, DFF_ID3_CHUNK)) {

                    tags.add(new TagInfo(TagFormat.DFF_METADATA, currentPos, chunkSize + DFF_CHUNK_HEADER_SIZE));

                    String chunkName = new String(dffChunkId, StandardCharsets.US_ASCII);
                    LOG.debug("Found DFF metadata chunk: {} at offset: {}, size: {} bytes",
                            chunkName, currentPos, chunkSize + DFF_CHUNK_HEADER_SIZE);
                }

                currentPos += DFF_CHUNK_HEADER_SIZE + chunkSize;
                if (chunkSize % 2 != 0) {
                    currentPos++;
                }
            }

        } catch (IOException e) {
            LOG.error("Error detecting DFF tags in {}: {}", source.name(), e.getMessage());
            throw e;
        }

        return tags;
    }

    private long readLittleEndianLong(byte[] data, int offset) {
        return ((long)(data[offset] & 0xFF)) |
                ((long)(data[offset + 1] & 0xFF) << 8) |
                ((long)(data[offset + 2] & 0xFF) << 16) |
                ((long)(data[offset + 3] & 0xFF) << 24) |
                ((long)(data[offset + 4] & 0xFF) << 32) |
                ((long)(data[offset + 5] & 0xFF) << 40) |
                ((long)(data[offset + 6] & 0xFF) << 48) |
                ((long)(data[offset + 7] & 0xFF) << 56);
    }

    private long readBigEndianLong(byte[] data, int offset) {
        return ((long)(data[offset] & 0xFF) << 56) |
                ((long)(data[offset + 1] & 0xFF) << 48) |
                ((long)(data[offset + 2] & 0xFF) << 40) |
                ((long)(data[offset + 3] & 0xFF) << 32) |
                ((long)(data[offset + 4] & 0xFF) << 24) |
                ((long)(data[offset + 5] & 0xFF) << 16) |
                ((long)(data[offset + 6] & 0xFF) << 8) |
                ((long)(data[offset + 7] & 0xFF));
    }
}