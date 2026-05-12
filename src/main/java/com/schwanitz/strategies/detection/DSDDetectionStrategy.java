package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Detection Strategy for DSD (Direct Stream Digital) formats
 * <p>
 * Supports two DSD container formats:
 * 1. DSF (DSD Stream File):
 *    - Header: "DSD " (0x44534420)
 *    - Chunk-based structure similar to WAV/RIFF
 *    - Metadata in "ID3 " chunk (ID3v2 tags)
 * <p>
 * 2. DFF (DSDIFF - DSD Interchange File Format):
 *    - Header: "FRM8" + "DSD " (AIFF-like)
 *    - Chunk-based structure
 *    - Metadata in various chunks (DIIN, DITI, etc.)
 */
public class DSDDetectionStrategy extends TagDetectionStrategy {

    // DSF Format Signatures
    private static final byte[] DSF_SIGNATURE = {'D', 'S', 'D', ' '};
    private static final byte[] DSF_ID3_CHUNK = {'I', 'D', '3', ' '};

    // DFF/DSDIFF Format Signatures
    private static final byte[] DFF_FORM_SIGNATURE = {'F', 'R', 'M', '8'};
    private static final byte[] DFF_DSD_TYPE = {'D', 'S', 'D', ' '};

    // DFF Metadata Chunks
    private static final byte[] DFF_DIIN_CHUNK = {'D', 'I', 'I', 'N'};
    private static final byte[] DFF_DITI_CHUNK = {'D', 'I', 'T', 'I'};
    private static final byte[] DFF_ID3_CHUNK = {'I', 'D', '3', ' '};

    // DSF structural offsets
    private static final int DSF_METADATA_POINTER_OFFSET = 20;
    private static final int DSF_ID3_CHUNK_HEADER_SIZE = 12; // 4 (chunk ID) + 8 (size)

    // DFF structural sizes
    private static final int DFF_FORM_HEADER_SIZE = 16; // 4 (FORM) + 8 (size) + 4 (DSD )
    private static final int DFF_CHUNK_HEADER_SIZE = 12;  // 4 (chunk ID) + 8 (size)
    private static final int DFF_MIN_TYPE_LENGTH = 8;     // 4 (FORM) + 4 (DSD)

    // Minimum buffer sizes for detection
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
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath,
                                    byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        if (!canDetect(startBuffer, endBuffer)) {
            return tags;
        }

        LOG.debug("Detecting DSD tags in file: {}", filePath);

        if (Arrays.equals(Arrays.copyOfRange(startBuffer, 0, 4), DSF_SIGNATURE)) {
            tags.addAll(detectDSFTags(file, filePath));
        } else if (Arrays.equals(Arrays.copyOfRange(startBuffer, 0, 4), DFF_FORM_SIGNATURE)) {
            tags.addAll(detectDFFTags(file, filePath));
        }

        return tags;
    }

    private List<TagInfo> detectDSFTags(RandomAccessFile file, String filePath) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        try {
            file.seek(0);

            byte[] signature = new byte[4];
            file.read(signature);

            if (!Arrays.equals(signature, DSF_SIGNATURE)) {
                return tags;
            }

            file.seek(DSF_METADATA_POINTER_OFFSET);
            long metadataPointer = readLittleEndianLong(file);

            if (metadataPointer > 0 && metadataPointer < file.length()) {
                file.seek(metadataPointer);

                byte[] chunkId = new byte[4];
                file.read(chunkId);

                if (Arrays.equals(chunkId, DSF_ID3_CHUNK)) {
                    long id3ChunkSize = readLittleEndianLong(file);

                    if (id3ChunkSize > 0 && id3ChunkSize < file.length() - metadataPointer) {
                        tags.add(new TagInfo(TagFormat.DSF_METADATA, metadataPointer, id3ChunkSize + DSF_ID3_CHUNK_HEADER_SIZE));
                        LOG.debug("Found DSF ID3 metadata at offset: {}, size: {} bytes",
                                metadataPointer, id3ChunkSize + DSF_ID3_CHUNK_HEADER_SIZE);
                    }
                }
            }

        } catch (IOException e) {
            LOG.error("Error detecting DSF tags in {}: {}", filePath, e.getMessage());
            throw e;
        }

        return tags;
    }

    private List<TagInfo> detectDFFTags(RandomAccessFile file, String filePath) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        try {
            file.seek(0);

            byte[] formSignature = new byte[4];
            file.read(formSignature);

            if (!Arrays.equals(formSignature, DFF_FORM_SIGNATURE)) {
                return tags;
            }

            long formSize = readBigEndianLong(file);

            byte[] dsdType = new byte[4];
            file.read(dsdType);

            if (!Arrays.equals(dsdType, DFF_DSD_TYPE)) {
                return tags;
            }

            long currentPos = DFF_FORM_HEADER_SIZE;
            long endPos = DFF_CHUNK_HEADER_SIZE + formSize;

            while (currentPos + DFF_CHUNK_HEADER_SIZE < endPos) {
                file.seek(currentPos);

                byte[] chunkId = new byte[4];
                file.read(chunkId);

                long chunkSize = readBigEndianLong(file);

                if (chunkSize < 0 || chunkSize > endPos - currentPos - DFF_CHUNK_HEADER_SIZE) {
                    break;
                }

                if (Arrays.equals(chunkId, DFF_DIIN_CHUNK) ||
                        Arrays.equals(chunkId, DFF_DITI_CHUNK) ||
                        Arrays.equals(chunkId, DFF_ID3_CHUNK)) {

                    tags.add(new TagInfo(TagFormat.DFF_METADATA, currentPos, chunkSize + DFF_CHUNK_HEADER_SIZE));

                    String chunkName = new String(chunkId, StandardCharsets.US_ASCII);
                    LOG.debug("Found DFF metadata chunk: {} at offset: {}, size: {} bytes",
                            chunkName, currentPos, chunkSize + DFF_CHUNK_HEADER_SIZE);
                }

                currentPos += DFF_CHUNK_HEADER_SIZE + chunkSize;
                if (chunkSize % 2 != 0) {
                    currentPos++;
                }
            }

        } catch (IOException e) {
            LOG.error("Error detecting DFF tags in {}: {}", filePath, e.getMessage());
            throw e;
        }

        return tags;
    }

    private long readLittleEndianLong(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[8];
        file.read(bytes);
        return ((long)(bytes[0] & 0xFF)) |
                ((long)(bytes[1] & 0xFF) << 8) |
                ((long)(bytes[2] & 0xFF) << 16) |
                ((long)(bytes[3] & 0xFF) << 24) |
                ((long)(bytes[4] & 0xFF) << 32) |
                ((long)(bytes[5] & 0xFF) << 40) |
                ((long)(bytes[6] & 0xFF) << 48) |
                ((long)(bytes[7] & 0xFF) << 56);
    }

    private long readBigEndianLong(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[8];
        file.read(bytes);
        return ((long)(bytes[0] & 0xFF) << 56) |
                ((long)(bytes[1] & 0xFF) << 48) |
                ((long)(bytes[2] & 0xFF) << 40) |
                ((long)(bytes[3] & 0xFF) << 32) |
                ((long)(bytes[4] & 0xFF) << 24) |
                ((long)(bytes[5] & 0xFF) << 16) |
                ((long)(bytes[6] & 0xFF) << 8) |
                ((long)(bytes[7] & 0xFF));
    }
}