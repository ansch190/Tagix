package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Detection Strategy für DSD (Direct Stream Digital) Formate
 *
 * Unterstützt zwei DSD Container-Formate:
 *
 * 1. DSF (DSD Stream File):
 *    - Header: "DSD " (0x44534420)
 *    - Chunk-basierte Struktur ähnlich WAV/RIFF
 *    - Metadaten in "ID3 " Chunk (ID3v2 Tags)
 *
 * 2. DFF (DSDIFF - DSD Interchange File Format):
 *    - Header: "FRM8" + "DSD " (AIFF-ähnlich)
 *    - Chunk-basierte Struktur
 *    - Metadaten in verschiedenen Chunks (DIIN, DITI, etc.)
 *
 * DSD ist ein 1-Bit Audio-Format mit sehr hohen Sampleraten (2.8224 MHz+)
 * Wird hauptsächlich für SACD (Super Audio CD) und High-End Audio verwendet.
 */
public class DSDDetectionStrategy extends TagDetectionStrategy {

    // DSF Format Signatures
    private static final byte[] DSF_SIGNATURE = {'D', 'S', 'D', ' '}; // "DSD "
    private static final byte[] DSF_ID3_CHUNK = {'I', 'D', '3', ' '}; // "ID3 "

    // DFF/DSDIFF Format Signatures
    private static final byte[] DFF_FORM_SIGNATURE = {'F', 'R', 'M', '8'}; // "FRM8"
    private static final byte[] DFF_DSD_TYPE = {'D', 'S', 'D', ' '}; // "DSD "

    // DFF Metadata Chunks
    private static final byte[] DFF_DIIN_CHUNK = {'D', 'I', 'I', 'N'}; // Edited Master Information
    private static final byte[] DFF_DITI_CHUNK = {'D', 'I', 'T', 'I'}; // Individual Track Information
    private static final byte[] DFF_EMID_CHUNK = {'E', 'M', 'I', 'D'}; // Edited Master ID
    private static final byte[] DFF_MARK_CHUNK = {'M', 'A', 'R', 'K'}; // Marker Information
    private static final byte[] DFF_ID3_CHUNK = {'I', 'D', '3', ' '};  // ID3 Tags in DFF

    @Override
    public List<TagFormat> getSupportedFormats() {
        return List.of(TagFormat.DSF_METADATA, TagFormat.DFF_METADATA);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (startBuffer.length < 8) {
            return false;
        }

        // DSF Format prüfen: "DSD "
        if (Arrays.equals(Arrays.copyOfRange(startBuffer, 0, 4), DSF_SIGNATURE)) {
            return true;
        }

        // DFF Format prüfen: "FRM8" + "DSD "
        if (Arrays.equals(Arrays.copyOfRange(startBuffer, 0, 4), DFF_FORM_SIGNATURE) &&
                startBuffer.length >= 12 &&
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

        Log.debug("Detecting DSD tags in file: {}", filePath);

        // DSF oder DFF Format bestimmen
        if (Arrays.equals(Arrays.copyOfRange(startBuffer, 0, 4), DSF_SIGNATURE)) {
            tags.addAll(detectDSFTags(file, filePath));
        } else if (Arrays.equals(Arrays.copyOfRange(startBuffer, 0, 4), DFF_FORM_SIGNATURE)) {
            tags.addAll(detectDFFTags(file, filePath));
        }

        return tags;
    }

    /**
     * Erkennt Metadaten in DSF (DSD Stream File) Dateien
     */
    private List<TagInfo> detectDSFTags(RandomAccessFile file, String filePath) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        try {
            file.seek(0);

            // DSF Header validieren
            byte[] signature = new byte[4];
            file.read(signature);

            if (!Arrays.equals(signature, DSF_SIGNATURE)) {
                Log.debug("Invalid DSF signature");
                return tags;
            }

            // DSF Header Size (4 bytes, Little-Endian)
            long headerSize = readLittleEndianInt(file) & 0xFFFFFFFFL;

            // Total File Size (8 bytes, Little-Endian)
            long totalFileSize = readLittleEndianLong(file);

            // Metadaten Pointer (8 bytes, Little-Endian)
            long metadataPointer = readLittleEndianLong(file);

            Log.debug("DSF Header: headerSize={}, totalSize={}, metadataPtr={}",
                    headerSize, totalFileSize, metadataPointer);

            // Sanity checks
            if (headerSize < 28 || totalFileSize != file.length() ||
                    metadataPointer < 0 || metadataPointer >= file.length()) {
                Log.warn("Invalid DSF header values");
                return tags;
            }

            // Wenn Metadaten-Pointer gesetzt ist (nicht 0)
            if (metadataPointer > 0) {
                file.seek(metadataPointer);

                // ID3 Chunk am Metadaten-Pointer suchen
                byte[] chunkId = new byte[4];
                file.read(chunkId);

                if (Arrays.equals(chunkId, DSF_ID3_CHUNK)) {
                    // ID3 Chunk Size (8 bytes, Little-Endian)
                    long id3ChunkSize = readLittleEndianLong(file);

                    if (id3ChunkSize > 0 && id3ChunkSize < file.length() - metadataPointer) {
                        tags.add(new TagInfo(TagFormat.DSF_METADATA, metadataPointer, id3ChunkSize + 12));
                        Log.debug("Found DSF ID3 metadata at offset: {}, size: {} bytes",
                                metadataPointer, id3ChunkSize + 12);
                    }
                } else {
                    Log.debug("No ID3 chunk found at metadata pointer in DSF file");
                }
            } else {
                Log.debug("No metadata pointer set in DSF file");
            }

            // Zusätzlich nach ID3 Chunks im gesamten File suchen (Fallback)
            List<TagInfo> additionalTags = scanForID3ChunksInDSF(file);
            tags.addAll(additionalTags);

        } catch (IOException e) {
            Log.error("Error detecting DSF tags in {}: {}", filePath, e.getMessage());
            throw e;
        }

        return tags;
    }

    /**
     * Erkennt Metadaten in DFF (DSDIFF) Dateien
     */
    private List<TagInfo> detectDFFTags(RandomAccessFile file, String filePath) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        try {
            file.seek(0);

            // DFF Header validieren
            byte[] formSignature = new byte[4];
            file.read(formSignature);

            if (!Arrays.equals(formSignature, DFF_FORM_SIGNATURE)) {
                Log.debug("Invalid DFF FORM signature");
                return tags;
            }

            // Form Size (8 bytes, Big-Endian)
            long formSize = readBigEndianLong(file);

            // DSD Type (4 bytes)
            byte[] dsdType = new byte[4];
            file.read(dsdType);

            if (!Arrays.equals(dsdType, DFF_DSD_TYPE)) {
                Log.debug("Invalid DFF DSD type");
                return tags;
            }

            Log.debug("DFF Header: formSize={}", formSize);

            // Sanity check
            if (formSize < 4 || formSize > file.length() - 12) {
                Log.warn("Invalid DFF form size: {}", formSize);
                return tags;
            }

            // Chunks im DFF Container durchsuchen
            long currentPos = 16; // Nach FRM8 + Size + DSD
            long endPos = 12 + formSize; // 12 = FRM8 + Size header

            while (currentPos + 12 < endPos) {
                file.seek(currentPos);

                // Chunk ID (4 bytes)
                byte[] chunkId = new byte[4];
                file.read(chunkId);

                // Chunk Size (8 bytes, Big-Endian)
                long chunkSize = readBigEndianLong(file);

                if (chunkSize < 0 || chunkSize > endPos - currentPos - 12) {
                    Log.warn("Invalid DFF chunk size: {} at position: {}", chunkSize, currentPos);
                    break;
                }

                // Bekannte Metadaten-Chunks prüfen
                if (Arrays.equals(chunkId, DFF_DIIN_CHUNK) ||
                        Arrays.equals(chunkId, DFF_DITI_CHUNK) ||
                        Arrays.equals(chunkId, DFF_EMID_CHUNK) ||
                        Arrays.equals(chunkId, DFF_MARK_CHUNK) ||
                        Arrays.equals(chunkId, DFF_ID3_CHUNK)) {

                    tags.add(new TagInfo(TagFormat.DFF_METADATA, currentPos, chunkSize + 12));

                    String chunkName = new String(chunkId);
                    Log.debug("Found DFF metadata chunk: {} at offset: {}, size: {} bytes",
                            chunkName, currentPos, chunkSize + 12);
                }

                // Zum nächsten Chunk (mit Padding für gerade Byte-Grenze)
                currentPos += 12 + chunkSize;
                if (chunkSize % 2 != 0) {
                    currentPos++; // Padding byte
                }
            }

        } catch (IOException e) {
            Log.error("Error detecting DFF tags in {}: {}", filePath, e.getMessage());
            throw e;
        }

        return tags;
    }

    /**
     * Scannt DSF-Datei nach ID3 Chunks (Fallback-Methode)
     */
    private List<TagInfo> scanForID3ChunksInDSF(RandomAccessFile file) throws IOException {
        List<TagInfo> tags = new ArrayList<>();

        long fileLength = file.length();
        long position = 28; // Nach DSF Header

        while (position + 12 < fileLength) {
            file.seek(position);

            byte[] chunkId = new byte[4];
            int bytesRead = file.read(chunkId);

            if (bytesRead != 4) {
                break;
            }

            if (Arrays.equals(chunkId, DSF_ID3_CHUNK)) {
                long chunkSize = readLittleEndianLong(file);

                if (chunkSize > 0 && chunkSize < fileLength - position - 12) {
                    tags.add(new TagInfo(TagFormat.DSF_METADATA, position, chunkSize + 12));
                    Log.debug("Found additional DSF ID3 chunk at offset: {}, size: {} bytes",
                            position, chunkSize + 12);

                    position += 12 + chunkSize;
                } else {
                    position += 4;
                }
            } else {
                position += 4;
            }
        }

        return tags;
    }

    /**
     * Liest einen 32-bit Little-Endian Integer
     */
    private int readLittleEndianInt(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[4];
        file.read(bytes);
        return ((bytes[0] & 0xFF)) |
                ((bytes[1] & 0xFF) << 8) |
                ((bytes[2] & 0xFF) << 16) |
                ((bytes[3] & 0xFF) << 24);
    }

    /**
     * Liest einen 64-bit Little-Endian Long
     */
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

    /**
     * Liest einen 64-bit Big-Endian Long
     */
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