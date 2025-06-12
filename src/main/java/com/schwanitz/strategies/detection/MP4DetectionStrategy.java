package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Detection Strategy for MP4 container metadata
 * <p>
 * MP4 files use atom-based structure with metadata in:
 * moov -> udta -> meta -> ilst
 * <p>
 * Key atoms:
 * - ftyp: File type identifier (at file start)
 * - moov: Movie atom containing metadata
 * - udta: User data atom
 * - meta: Metadata atom
 * - ilst: Item list atom (contains actual metadata)
 */
public class MP4DetectionStrategy extends TagDetectionStrategy {

    private static final int ATOM_HEADER_SIZE = 8;
    private static final int ATOM_TYPE_LENGTH = 4;
    private static final long SEARCH_LIMIT = 64 * 1024; // 64 KB

    @Override
    public List<TagFormat> getSupportedTagFormats() {
        return List.of(TagFormat.MP4);
    }

    @Override
    public boolean canDetect(byte[] startBuffer, byte[] endBuffer) {
        if (startBuffer.length < ATOM_HEADER_SIZE) return false;
        return new String(startBuffer, ATOM_TYPE_LENGTH, ATOM_TYPE_LENGTH).equals("ftyp");
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        if (!canDetect(startBuffer, endBuffer)) return tags;

        Log.debug("Detecting MP4 tags in file: {}", filePath);

        // Try forward search in first 64 KB
        long fileLength = file.length();
        long position = 0;
        long searchEnd = Math.min(fileLength, SEARCH_LIMIT);

        while (position + ATOM_HEADER_SIZE < searchEnd) {
            TagInfo tag = tryReadAtom(file, position, fileLength);
            if (tag != null) {
                tags.add(tag);
                Log.debug("Found moov atom at offset: {}, size: {} bytes", tag.getOffset(), tag.getSize());
                return tags;
            }
            position += ATOM_HEADER_SIZE; // Move to next potential atom
        }

        // Fallback: Try backward search from file end
        position = fileLength - ATOM_HEADER_SIZE;
        while (position >= 0) {
            TagInfo tag = tryReadAtom(file, position, fileLength);
            if (tag != null) {
                tags.add(tag);
                Log.debug("Found moov atom at offset: {}, size: {} bytes", tag.getOffset(), tag.getSize());
                return tags;
            }
            position -= ATOM_HEADER_SIZE; // Move backward
        }

        Log.debug("No moov atom found in file: {}", filePath);
        return tags;
    }

    /**
     * Read and validate atom at given position
     */
    private TagInfo tryReadAtom(RandomAccessFile file, long position, long fileLength) throws IOException {
        file.seek(position);
        byte[] atomHeader = new byte[ATOM_HEADER_SIZE];
        if (file.read(atomHeader) != ATOM_HEADER_SIZE) {
            return null;
        }

        // Read atom size (32-bit big-endian)
        int atomSize = ((atomHeader[0] & 0xFF) << 24) |
                ((atomHeader[1] & 0xFF) << 16) |
                ((atomHeader[2] & 0xFF) << 8) |
                (atomHeader[3] & 0xFF);

        // Validate atom size
        if (atomSize < ATOM_HEADER_SIZE || position + atomSize > fileLength) {
            Log.debug("Invalid atom size: {} at offset: {}", atomSize, position);
            return null;
        }

        // Read an atom type
        String atomType = new String(atomHeader, ATOM_TYPE_LENGTH, ATOM_TYPE_LENGTH);
        if (!atomType.equals("moov")) {
            return null;
        }

        // Ensure moov is a top-level atom (not nested)
        if (position > 0) {
            file.seek(position - ATOM_HEADER_SIZE);
            byte[] prevHeader = new byte[ATOM_HEADER_SIZE];
            if (file.read(prevHeader) == ATOM_HEADER_SIZE) {
                int prevSize = ((prevHeader[0] & 0xFF) << 24) |
                        ((prevHeader[1] & 0xFF) << 16) |
                        ((prevHeader[2] & 0xFF) << 8) |
                        (prevHeader[3] & 0xFF);
                if (prevSize > position) {
                    Log.debug("moov at offset: {} appears nested, skipping", position);
                    return null;
                }
            }
        }

        return new TagInfo(TagFormat.MP4, position, atomSize);
    }
}