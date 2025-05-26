package com.schwanitz.strategies.detection;

import com.schwanitz.strategies.detection.context.FormatDetectionStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MP4DetectionStrategy implements FormatDetectionStrategy {
    private static final Logger Log = LoggerFactory.getLogger(MP4DetectionStrategy.class);
    private static final Set<String> VALID_MP4_BRANDS = new HashSet<>(Arrays.asList(
            "M4A ", "mp42", "isom", "qt  ", "3gp4", "3gp6", "m4v "
    ));
    private static final long MAX_MP4_SEARCH = 1024 * 1024; // 1 MB für MP4 moov-Suche

    @Override
    public boolean canDetect(String fileExtension, byte[] startBuffer, byte[] endBuffer) {
        boolean isMP4 = (fileExtension.equals("m4a") || fileExtension.equals("mp4") || fileExtension.equals("m4v")) &&
                startBuffer.length >= 8 && new String(startBuffer, 4, 4).equals("ftyp");
        return isMP4;
    }

    @Override
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> detectedTags = new ArrayList<>();

        // MP4: MP4-Tags (Exakt die gleiche Logik wie vorher)
        TagInfo mp4Tag = checkMP4Tags(file);
        if (mp4Tag != null) {
            detectedTags.add(mp4Tag);
        }

        return detectedTags;
    }

    // Ursprüngliche checkMP4Tags Methode unverändert
    private static TagInfo checkMP4Tags(RandomAccessFile file) throws IOException {
        try {
            file.seek(0);
            byte[] buffer = new byte[12];
            file.read(buffer);
            if (!new String(buffer, 4, 4).equals("ftyp")) {
                Log.debug("Kein MP4-ftyp-Atom gefunden");
                return null;
            }

            // Prüfe Major Brand
            String majorBrand = new String(buffer, 8, 4);
            if (!VALID_MP4_BRANDS.contains(majorBrand)) {
                Log.debug("Ungültiger MP4-Major-Brand: " + majorBrand);
                return null;
            }

            // Parse Atomstruktur, um moov-Atom zu finden (max. 1 MB)
            long position = 0;
            while (position + 8 < file.length() && position < MAX_MP4_SEARCH) {
                file.seek(position);
                byte[] atomHeader = new byte[8];
                file.read(atomHeader);
                int atomSize = ((atomHeader[0] & 0xFF) << 24) | ((atomHeader[1] & 0xFF) << 16) |
                        ((atomHeader[2] & 0xFF) << 8) | (atomHeader[3] & 0xFF);
                String atomType = new String(atomHeader, 4, 4);

                if (atomSize < 8 || atomSize > file.length() - position) {
                    Log.debug("Ungültige Atomgröße: " + atomSize + " an Position " + position);
                    return null;
                }

                if (atomType.equals("moov")) {
                    return new TagInfo(TagFormat.MP4, position, atomSize);
                }
                position += atomSize;
            }
            Log.debug("Kein moov-Atom in MP4-Datei gefunden");
            return null;
        } catch (IOException e) {
            Log.warn("Fehler bei MP4-Tag-Prüfung: " + e.getMessage());
            return null;
        }
    }
}