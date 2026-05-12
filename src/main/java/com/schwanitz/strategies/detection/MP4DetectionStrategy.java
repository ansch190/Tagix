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
 * Detection Strategy for MP4 container metadata
 * <p>
 * MP4 files use an atom-based (box-based) structure where each atom consists of:
 * - Size: 4 bytes big-endian (total atom size including header)
 * - Type: 4 bytes ASCII (e.g. "ftyp", "moov", "mdat")
 * - If size == 1: Extended size follows as 8 bytes big-endian
 * - If size == 0: Atom extends to end of file
 * <p>
 * Metadata is stored in:
 * moov -> udta -> meta -> ilst
 * <p>
 * The moov atom can appear anywhere in the file. In streaming-optimized
 * files it appears near the beginning; in others it appears at the end
 * after the mdat atom.
 */
public class MP4DetectionStrategy extends TagDetectionStrategy {

    private static final int ATOM_HEADER_SIZE = 8;
    private static final int ATOM_TYPE_LENGTH = 4;
    private static final int EXTENDED_SIZE_LENGTH = 8;

    // Special size values per ISO 14496-12
    private static final int SIZE_EXTENDED = 1;
    private static final int SIZE_EOF = 0;

    // Safety limit: stop scanning after this many top-level atoms
    private static final int MAX_TOP_LEVEL_ATOMS = 5000;

    // Safety limit: maximum reasonable atom size (2 GB)
    private static final long MAX_REASONABLE_ATOM_SIZE = 2L * 1024 * 1024 * 1024;

    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.MP4);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (startBuffer.length < ATOM_HEADER_SIZE) return false;
        return new String(startBuffer, ATOM_TYPE_LENGTH, ATOM_TYPE_LENGTH, StandardCharsets.US_ASCII).equals("ftyp");
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        if (!canDetect(startBuffer, endBuffer)) return tags;

        LOG.debug("Detecting MP4 tags in file: {}", filePath);

        long fileLength = file.length();
        long position = 0;
        int atomCount = 0;

        while (position + ATOM_HEADER_SIZE <= fileLength && atomCount < MAX_TOP_LEVEL_ATOMS) {
            file.seek(position);
            byte[] atomHeader = new byte[ATOM_HEADER_SIZE];
            if (file.read(atomHeader) != ATOM_HEADER_SIZE) {
                break;
            }

            long atomSize = readAtomSize(atomHeader, file, position, fileLength);
            String atomType = new String(atomHeader, ATOM_TYPE_LENGTH, ATOM_TYPE_LENGTH, StandardCharsets.US_ASCII);

            if (atomSize < ATOM_HEADER_SIZE && atomSize != SIZE_EOF) {
                LOG.debug("Invalid atom size {} at offset {}, stopping scan", atomSize, position);
                break;
            }

            if (atomSize > MAX_REASONABLE_ATOM_SIZE) {
                LOG.debug("Unreasonable atom size {} at offset {}, stopping scan", atomSize, position);
                break;
            }

            if (atomType.equals("moov")) {
                long effectiveSize = (atomSize == SIZE_EOF) ? fileLength - position : atomSize;
                tags.add(new TagInfo(TagFormat.MP4, position, effectiveSize));
                LOG.debug("Found moov atom at offset: {}, size: {} bytes", position, effectiveSize);
                return tags;
            }

            if (atomSize == SIZE_EOF) {
                break;
            }

            position += atomSize;
            atomCount++;
        }

        LOG.debug("No moov atom found in file: {}", filePath);
        return tags;
    }

    /**
     * Read atom size, handling extended (64-bit) sizes.
     * <p>
     * Per ISO 14496-12:
     * - size == 0: atom extends to end of file
     * - size == 1: next 8 bytes contain the actual 64-bit size
     * - otherwise: size is the 32-bit value from the header
     */
    private long readAtomSize(byte[] atomHeader, RandomAccessFile file, long position, long fileLength) throws IOException {
        int rawSize = ((atomHeader[0] & 0xFF) << 24) |
                ((atomHeader[1] & 0xFF) << 16) |
                ((atomHeader[2] & 0xFF) << 8) |
                (atomHeader[3] & 0xFF);

        if (rawSize == SIZE_EOF) {
            return SIZE_EOF;
        }

        if (rawSize == SIZE_EXTENDED) {
            byte[] extendedSize = new byte[EXTENDED_SIZE_LENGTH];
            if (file.read(extendedSize) != EXTENDED_SIZE_LENGTH) {
                return -1;
            }
            long size = ((long)(extendedSize[0] & 0xFF) << 56) |
                    ((long)(extendedSize[1] & 0xFF) << 48) |
                    ((long)(extendedSize[2] & 0xFF) << 40) |
                    ((long)(extendedSize[3] & 0xFF) << 32) |
                    ((long)(extendedSize[4] & 0xFF) << 24) |
                    ((long)(extendedSize[5] & 0xFF) << 16) |
                    ((long)(extendedSize[6] & 0xFF) << 8) |
                    ((long)(extendedSize[7] & 0xFF));

            if (size < ATOM_HEADER_SIZE + EXTENDED_SIZE_LENGTH) {
                LOG.debug("Invalid extended atom size {} at offset {}", size, position);
                return -1;
            }

            return size;
        }

        if (rawSize < ATOM_HEADER_SIZE) {
            LOG.debug("Invalid atom size {} at offset {}", rawSize, position);
            return -1;
        }

        return rawSize;
    }
}