package com.schwanitz.tagging;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Verwaltet die Prioritäten von Tag-Formaten für verschiedene Dateitypen.
 * <p>
 * Stellt die Reihenfolge bereit, in der Tag-Formate bei der Erkennung geprüft werden,
 * sowohl für den Vollständigen Scan (allgemeine Priorität nach Häufigkeit) als auch
 * für den Komfort-Scan (dateitypspezifische Priorität).
 * <p>
 * Unterstützte Formate umfassen: ID3, APE, Vorbis Comment, MP4, RIFF/BWF,
 * AIFF, Lyrics3, ASF/WMA, FLAC Application, Matroska/WebM, DSD, TTA und WavPack.
 */
public class FormatPriorityManager {

    /** Allgemeine Prioritätsliste für den Full Scan, sortiert nach Häufigkeit und Wahrscheinlichkeit. */
    private static final List<TagFormat> FULL_SCAN_PRIORITY = Arrays.asList(
            TagFormat.ID3V2_3,
            TagFormat.ID3V2_4,
            TagFormat.ID3V1,
            TagFormat.ID3V1_1,
            TagFormat.ID3V2_2,

            TagFormat.VORBIS_COMMENT,

            TagFormat.MP4,

            TagFormat.APEV2,
            TagFormat.APEV1,

            TagFormat.ASF_CONTENT_DESC,
            TagFormat.ASF_EXT_CONTENT_DESC,

            TagFormat.RIFF_INFO,
            TagFormat.BWF_V2,
            TagFormat.BWF_V1,
            TagFormat.BWF_V0,

            TagFormat.FLAC_APPLICATION,

            TagFormat.MATROSKA_TAGS,
            TagFormat.WEBM_TAGS,

            TagFormat.DSF_METADATA,
            TagFormat.DFF_METADATA,

            TagFormat.WAVPACK_NATIVE,
            TagFormat.TTA_METADATA,

            TagFormat.AIFF_METADATA,
            TagFormat.LYRICS3V2,
            TagFormat.LYRICS3V1
    );

    /** Prioritätslisten nach Dateiendung für den Komfort-Scan. */
    private static final Map<String, List<TagFormat>> FILE_EXTENSION_PRIORITIES = new HashMap<>();

    static {
        FILE_EXTENSION_PRIORITIES.put("mp3", Arrays.asList(
                TagFormat.ID3V2_3,
                TagFormat.ID3V2_4,
                TagFormat.ID3V1,
                TagFormat.ID3V1_1,
                TagFormat.ID3V2_2,
                TagFormat.APEV2,
                TagFormat.APEV1,
                TagFormat.LYRICS3V2,
                TagFormat.LYRICS3V1
        ));

        FILE_EXTENSION_PRIORITIES.put("wav", Arrays.asList(
                TagFormat.RIFF_INFO,
                TagFormat.BWF_V2,
                TagFormat.BWF_V1,
                TagFormat.BWF_V0,
                TagFormat.ID3V2_3,
                TagFormat.ID3V2_4,
                TagFormat.ID3V1,
                TagFormat.ID3V1_1
        ));

        FILE_EXTENSION_PRIORITIES.put("ogg", List.of(
                TagFormat.VORBIS_COMMENT
        ));

        FILE_EXTENSION_PRIORITIES.put("spx", List.of(
                TagFormat.VORBIS_COMMENT
        ));

        FILE_EXTENSION_PRIORITIES.put("opus", List.of(
                TagFormat.VORBIS_COMMENT
        ));

        FILE_EXTENSION_PRIORITIES.put("flac", Arrays.asList(
                TagFormat.VORBIS_COMMENT,
                TagFormat.FLAC_APPLICATION,
                TagFormat.ID3V2_3,
                TagFormat.ID3V2_4,
                TagFormat.APEV2,
                TagFormat.APEV1
        ));

        FILE_EXTENSION_PRIORITIES.put("mp4", List.of(
                TagFormat.MP4
        ));

        FILE_EXTENSION_PRIORITIES.put("m4a", List.of(
                TagFormat.MP4
        ));

        FILE_EXTENSION_PRIORITIES.put("m4v", List.of(
                TagFormat.MP4
        ));

        FILE_EXTENSION_PRIORITIES.put("wma", Arrays.asList(
                TagFormat.ASF_CONTENT_DESC,
                TagFormat.ASF_EXT_CONTENT_DESC
        ));

        FILE_EXTENSION_PRIORITIES.put("asf", Arrays.asList(
                TagFormat.ASF_CONTENT_DESC,
                TagFormat.ASF_EXT_CONTENT_DESC
        ));

        FILE_EXTENSION_PRIORITIES.put("wmv", Arrays.asList(
                TagFormat.ASF_CONTENT_DESC,
                TagFormat.ASF_EXT_CONTENT_DESC
        ));

        FILE_EXTENSION_PRIORITIES.put("mkv", List.of(
                TagFormat.MATROSKA_TAGS
        ));

        FILE_EXTENSION_PRIORITIES.put("mka", List.of(
                TagFormat.MATROSKA_TAGS
        ));

        FILE_EXTENSION_PRIORITIES.put("mks", List.of(
                TagFormat.MATROSKA_TAGS
        ));

        FILE_EXTENSION_PRIORITIES.put("webm", List.of(
                TagFormat.WEBM_TAGS
        ));

        FILE_EXTENSION_PRIORITIES.put("dsf", List.of(
                TagFormat.DSF_METADATA
        ));

        FILE_EXTENSION_PRIORITIES.put("dff", List.of(
                TagFormat.DFF_METADATA
        ));

        FILE_EXTENSION_PRIORITIES.put("dsd", Arrays.asList(
                TagFormat.DSF_METADATA,
                TagFormat.DFF_METADATA
        ));

        FILE_EXTENSION_PRIORITIES.put("tta", Arrays.asList(
                TagFormat.TTA_METADATA,
                TagFormat.APEV2,
                TagFormat.APEV1,
                TagFormat.ID3V2_3,
                TagFormat.ID3V2_4,
                TagFormat.ID3V1,
                TagFormat.ID3V1_1
        ));

        FILE_EXTENSION_PRIORITIES.put("wv", Arrays.asList(
                TagFormat.WAVPACK_NATIVE,
                TagFormat.APEV2,
                TagFormat.APEV1,
                TagFormat.ID3V2_3,
                TagFormat.ID3V2_4,
                TagFormat.ID3V1,
                TagFormat.ID3V1_1
        ));

        FILE_EXTENSION_PRIORITIES.put("aiff", Arrays.asList(
                TagFormat.AIFF_METADATA,
                TagFormat.ID3V2_3,
                TagFormat.ID3V2_4,
                TagFormat.ID3V1,
                TagFormat.ID3V1_1
        ));

        FILE_EXTENSION_PRIORITIES.put("aif", Arrays.asList(
                TagFormat.AIFF_METADATA,
                TagFormat.ID3V2_3,
                TagFormat.ID3V2_4,
                TagFormat.ID3V1,
                TagFormat.ID3V1_1
        ));

        FILE_EXTENSION_PRIORITIES.put("ape", Arrays.asList(
                TagFormat.APEV2,
                TagFormat.APEV1,
                TagFormat.ID3V2_3,
                TagFormat.ID3V2_4,
                TagFormat.ID3V1,
                TagFormat.ID3V1_1
        ));

        FILE_EXTENSION_PRIORITIES.put("mpc", Arrays.asList(
                TagFormat.APEV2,
                TagFormat.APEV1,
                TagFormat.ID3V2_3,
                TagFormat.ID3V2_4,
                TagFormat.ID3V1,
                TagFormat.ID3V1_1
        ));

        FILE_EXTENSION_PRIORITIES.put("ofr", Arrays.asList(
                TagFormat.APEV2,
                TagFormat.APEV1,
                TagFormat.ID3V2_3,
                TagFormat.ID3V2_4
        ));

        FILE_EXTENSION_PRIORITIES.put("shn", Arrays.asList(
                TagFormat.APEV2,
                TagFormat.APEV1,
                TagFormat.ID3V2_3,
                TagFormat.ID3V2_4
        ));
    }

    /**
     * Gibt die Prioritätsliste für den Vollständigen Scan zurück.
     * <p>
     * Die Liste ist nach allgemeiner Häufigkeit und Wahrscheinlichkeit der Formate sortiert.
     * Die zurückgegebene Liste ist eine Kopie und kann frei modifiziert werden.
     *
     * @return eine neue Zeichenkette mit Tag-Formaten in Prioritätsreihenfolge
     */
    public static List<TagFormat> getFullScanPriority() {
        return new ArrayList<>(FULL_SCAN_PRIORITY);
    }

    /**
     * Gibt die Prioritätsliste für eine bestimmte Dateiendung zurück (Komfort-Scan).
     * <p>
     * Wenn die Dateiendung nicht bekannt ist, wird als Fallback die vollständige
     * Prioritätsliste von {@link #getFullScanPriority()} zurückgegeben.
     *
     * @param fileExtension die Dateiendung ohne Punkt (z. B. "mp3", "flac"),
     *                      Groß-/Kleinschreibung wird ignoriert
     * @return eine neue Liste mit Tag-Formaten in Prioritätsreihenfolge für den Dateityp
     */
    public static List<TagFormat> getComfortScanPriority(String fileExtension) {
        String extension = fileExtension.toLowerCase();
        List<TagFormat> priorities = FILE_EXTENSION_PRIORITIES.get(extension);
        if (priorities == null) {
            return getFullScanPriority();
        }
        return new ArrayList<>(priorities);
    }

    /**
     * Gibt alle unterstützten Dateiendungen zurück, für die eine Komfort-Scan-Priorität
     * definiert ist.
     *
     * @return eine Liste aller unterstützten Dateiendungen
     */
    public static List<String> getSupportedExtensions() {
        return new ArrayList<>(FILE_EXTENSION_PRIORITIES.keySet());
    }

}