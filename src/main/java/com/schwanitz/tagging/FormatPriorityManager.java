package com.schwanitz.tagging;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Verwaltet die Prioritäten von Tag-Formaten für verschiedene Dateitypen
 */
public class FormatPriorityManager {

    // Allgemeine Prioritätsliste für Full Scan (nach Häufigkeit/Wahrscheinlichkeit)
    private static final List<TagFormat> FULL_SCAN_PRIORITY = Arrays.asList(
            // ID3 Formate (sehr häufig)
            TagFormat.ID3V2_3,        // Häufigstes ID3 Format
            TagFormat.ID3V2_4,        // Modernes ID3 Format
            TagFormat.ID3V1,          // Legacy ID3
            TagFormat.ID3V1_1,        // ID3v1 mit Track-Nummer
            TagFormat.ID3V2_2,        // Älteres ID3v2

            // Vorbis Comments (OGG/FLAC - sehr verbreitet)
            TagFormat.VORBIS_COMMENT,

            // MP4/iTunes Tags (sehr verbreitet bei Apple-Ökosystem)
            TagFormat.MP4,

            // APE Tags (bei verlustfreien Formaten beliebt)
            TagFormat.APEV2,
            TagFormat.APEV1,

            // RIFF/WAV Formate
            TagFormat.RIFF_INFO,
            TagFormat.BWF_V2,         // Neueste BWF Version
            TagFormat.BWF_V1,
            TagFormat.BWF_V0,

            // Seltener verwendete Formate
            TagFormat.AIFF_METADATA,
            TagFormat.LYRICS3V2,
            TagFormat.LYRICS3V1
    );

    // Prioritätslisten nach Dateiendung
    private static final Map<String, List<TagFormat>> FILE_EXTENSION_PRIORITIES = new HashMap<>();

    static {
        // MP3 Dateien
        FILE_EXTENSION_PRIORITIES.put("mp3", Arrays.asList(
                TagFormat.ID3V2_3,        // Standard für MP3
                TagFormat.ID3V2_4,        // Moderner Standard
                TagFormat.ID3V1,          // Legacy Support
                TagFormat.ID3V1_1,        // ID3v1 mit Track
                TagFormat.ID3V2_2,        // Ältere Version
                TagFormat.APEV2,          // Alternative zu ID3
                TagFormat.APEV1,          // Ältere APE Version
                TagFormat.LYRICS3V2,      // Lyrics Support
                TagFormat.LYRICS3V1       // Legacy Lyrics
        ));

        // WAV Dateien
        FILE_EXTENSION_PRIORITIES.put("wav", Arrays.asList(
                TagFormat.RIFF_INFO,      // Standard für WAV
                TagFormat.BWF_V2,         // Broadcast Wave (modern)
                TagFormat.BWF_V1,         // Broadcast Wave
                TagFormat.BWF_V0,         // Broadcast Wave (legacy)
                TagFormat.ID3V2_3,        // ID3 in WAV möglich
                TagFormat.ID3V2_4,        // ID3 in WAV
                TagFormat.ID3V1,          // ID3v1 in WAV
                TagFormat.ID3V1_1         // ID3v1.1 in WAV
        ));

        // OGG Dateien (Ogg Vorbis, Speex, Opus)
        FILE_EXTENSION_PRIORITIES.put("ogg", Arrays.asList(
                TagFormat.VORBIS_COMMENT  // Einziger Standard für OGG
        ));

        FILE_EXTENSION_PRIORITIES.put("spx", Arrays.asList(
                TagFormat.VORBIS_COMMENT  // Speex verwendet Vorbis Comments
        ));

        FILE_EXTENSION_PRIORITIES.put("opus", Arrays.asList(
                TagFormat.VORBIS_COMMENT  // Opus verwendet Vorbis Comments
        ));

        // FLAC Dateien
        FILE_EXTENSION_PRIORITIES.put("flac", Arrays.asList(
                TagFormat.VORBIS_COMMENT, // Standard für FLAC
                TagFormat.ID3V2_3,        // Manchmal auch ID3 in FLAC
                TagFormat.ID3V2_4,        // ID3v2.4 in FLAC
                TagFormat.APEV2,          // APE Tags in FLAC
                TagFormat.APEV1           // APE v1 in FLAC
        ));

        // MP4/M4A Dateien
        FILE_EXTENSION_PRIORITIES.put("mp4", Arrays.asList(
                TagFormat.MP4             // Einziger Standard für MP4
        ));

        FILE_EXTENSION_PRIORITIES.put("m4a", Arrays.asList(
                TagFormat.MP4             // iTunes/AAC Standard
        ));

        FILE_EXTENSION_PRIORITIES.put("m4v", Arrays.asList(
                TagFormat.MP4             // Video MP4
        ));

        // AIFF Dateien
        FILE_EXTENSION_PRIORITIES.put("aiff", Arrays.asList(
                TagFormat.AIFF_METADATA,  // AIFF Standard
                TagFormat.ID3V2_3,        // ID3 in AIFF möglich
                TagFormat.ID3V2_4,        // ID3v2.4 in AIFF
                TagFormat.ID3V1,          // ID3v1 in AIFF
                TagFormat.ID3V1_1         // ID3v1.1 in AIFF
        ));

        FILE_EXTENSION_PRIORITIES.put("aif", Arrays.asList(
                TagFormat.AIFF_METADATA,  // AIFF Standard
                TagFormat.ID3V2_3,        // ID3 in AIFF möglich
                TagFormat.ID3V2_4,        // ID3v2.4 in AIFF
                TagFormat.ID3V1,          // ID3v1 in AIFF
                TagFormat.ID3V1_1         // ID3v1.1 in AIFF
        ));

        // APE Dateien (Monkey's Audio)
        FILE_EXTENSION_PRIORITIES.put("ape", Arrays.asList(
                TagFormat.APEV2,          // Standard für APE
                TagFormat.APEV1,          // Legacy APE
                TagFormat.ID3V2_3,        // Fallback
                TagFormat.ID3V2_4,        // Fallback
                TagFormat.ID3V1,          // Fallback
                TagFormat.ID3V1_1         // Fallback
        ));

        // WavPack Dateien
        FILE_EXTENSION_PRIORITIES.put("wv", Arrays.asList(
                TagFormat.APEV2,          // Standard für WavPack
                TagFormat.APEV1,          // Legacy APE
                TagFormat.ID3V2_3,        // Fallback
                TagFormat.ID3V2_4,        // Fallback
                TagFormat.ID3V1,          // Fallback
                TagFormat.ID3V1_1         // Fallback
        ));

        // Musepack Dateien
        FILE_EXTENSION_PRIORITIES.put("mpc", Arrays.asList(
                TagFormat.APEV2,          // Standard für Musepack
                TagFormat.APEV1,          // Legacy APE
                TagFormat.ID3V2_3,        // Fallback
                TagFormat.ID3V2_4,        // Fallback
                TagFormat.ID3V1,          // Fallback
                TagFormat.ID3V1_1         // Fallback
        ));
    }

    /**
     * Gibt die Prioritätsliste für Full Scan zurück
     */
    public static List<TagFormat> getFullScanPriority() {
        return new ArrayList<>(FULL_SCAN_PRIORITY);
    }

    /**
     * Gibt die Prioritätsliste für eine bestimmte Dateiendung zurück (Comfort Scan)
     */
    public static List<TagFormat> getComfortScanPriority(String fileExtension) {
        String extension = fileExtension.toLowerCase();
        List<TagFormat> priorities = FILE_EXTENSION_PRIORITIES.get(extension);

        if (priorities == null) {
            // Fallback: Wenn Dateiendung unbekannt, verwende Full Scan Priorität
            return getFullScanPriority();
        }

        return new ArrayList<>(priorities);
    }

}