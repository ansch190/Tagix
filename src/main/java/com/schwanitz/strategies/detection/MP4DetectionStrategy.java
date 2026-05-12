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
 * Erkennungsstrategie für MP4-Container-Metadaten.
 * <p>
 * MP4-Dateien verwenden eine Atom-basierte (Box-basierte) Struktur, bei der jedes Atom aus besteht:
 * <ul>
 *   <li>Größe: 4 Bytes Big-Endian (Gesamtgröße des Atoms inklusive Header)</li>
 *   <li>Typ: 4 Bytes ASCII (z. B. "ftyp", "moov", "mdat")</li>
 *   <li>Wenn Größe == 1: Erweiterte Größe folgt als 8 Bytes Big-Endian</li>
 *   <li>Wenn Größe == 0: Atom reicht bis zum Dateiende</li>
 * </ul>
 * <p>
 * Metadaten werden gespeichert in:
 * {@code moov -> udta -> meta -> ilst}
 * <p>
 * Das moov-Atom kann an beliebiger Stelle in der Datei stehen. In streaming-optimierten
 * Dateien steht es nahe dem Anfang, in anderen Dateien am Ende nach dem mdat-Atom.
 */
public class MP4DetectionStrategy extends TagDetectionStrategy {

    private static final int ATOM_HEADER_SIZE = 8;
    private static final int ATOM_TYPE_LENGTH = 4;
    private static final int EXTENDED_SIZE_LENGTH = 8;

    private static final int SIZE_EXTENDED = 1;
    private static final int SIZE_EOF = 0;

    private static final int MAX_TOP_LEVEL_ATOMS = 5000;

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
    public List<TagInfo> detectTags(SeekableDataSource source, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> tags = new ArrayList<>();
        if (!canDetect(startBuffer, endBuffer)) return tags;

        LOG.debug("Detecting MP4 tags in source: {}", source.name());

        long fileLength = source.length();
        long position = 0;
        int atomCount = 0;

        while (position + ATOM_HEADER_SIZE <= fileLength && atomCount < MAX_TOP_LEVEL_ATOMS) {
            byte[] atomHeader = new byte[ATOM_HEADER_SIZE];
            int bytesRead = source.read(position, atomHeader, 0, ATOM_HEADER_SIZE);
            if (bytesRead != ATOM_HEADER_SIZE) {
                break;
            }

            long atomSize = readAtomSize(atomHeader, source, position, fileLength);
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

        LOG.debug("No moov atom found in source: {}", source.name());
        return tags;
    }

    private long readAtomSize(byte[] atomHeader, SeekableDataSource source, long position, long fileLength) throws IOException {
        int rawSize = ((atomHeader[0] & 0xFF) << 24) |
                ((atomHeader[1] & 0xFF) << 16) |
                ((atomHeader[2] & 0xFF) << 8) |
                (atomHeader[3] & 0xFF);

        if (rawSize == SIZE_EOF) {
            return SIZE_EOF;
        }

        if (rawSize == SIZE_EXTENDED) {
            byte[] extendedSize = new byte[EXTENDED_SIZE_LENGTH];
            if (source.read(position + ATOM_HEADER_SIZE, extendedSize, 0, EXTENDED_SIZE_LENGTH) != EXTENDED_SIZE_LENGTH) {
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