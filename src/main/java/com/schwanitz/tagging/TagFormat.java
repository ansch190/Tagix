package com.schwanitz.tagging;

/**
 * Aufzählung aller unterstützten Audio-Tag-Formate.
 * <p>
 * Definiert die verschiedenen Tag-Formate, die in Audiodateien vorkommen können,
 * einschließlich ID3-, APE-, Vorbis-, MP4-, RIFF- und Container-spezifischer Formate.
 * Jede Konstante hält den Anzeigenamen des Formats.
 */
public enum TagFormat {

    /** ID3v1-Tag – ursprüngliches MP3-Metadatenformat mit festen 128 Byte am Dateiende, begrenzte Feldlängen. */
    ID3V1("ID3v1"),
    /** ID3v1.1-Tag – Erweiterung von ID3v1 um eine Tracknummer. */
    ID3V1_1("ID3v1.1"),
    /** ID3v2.2-Tag – frühe Version des ID3v2-Formats mit 3-Zeichen-Frames, am Dateianfang. */
    ID3V2_2("ID3v2.2"),
    /** ID3v2.3-Tag – weitverbreitetste ID3v2-Version mit 4-Zeichen-Frames, am Dateianfang. */
    ID3V2_3("ID3v2.3"),
    /** ID3v2.4-Tag – neueste ID3v2-Version mit Unicode-Unterstützung und UTF-8-Encoding. */
    ID3V2_4("ID3v2.4"),

    /** APEv1-Tag – Vorgänger von APEv2, wird in Monkey's Audio-Dateien verwendet. */
    APEV1("APEv1"),
    /** APEv2-Tag – flexibles Tag-Format mit Unicode-Unterstützung, häufig in Musepack- und WavPack-Dateien. */
    APEV2("APEv2"),

    /** Vorbis Comment – Standard-Tag-Format für OGG Vorbis, FLAC und Opus, Schlüssel-Wert-Paare. */
    VORBIS_COMMENT("VorbisComment"),

    /** MP4/iTunes-Tag – Metadaten im MP4-Container-Format, Standard für M4A/M4V-Dateien. */
    MP4("MP4"),

    /** RIFF INFO-Tag – Standard-Metadatenblock in WAV-Dateien nach dem RIFF-Standard. */
    RIFF_INFO("RIFFInfo"),
    /** BWFv0-Tag – Broadcast Wave Format Version 0, Erweiterung für professionelle WAV-Dateien. */
    BWF_V0("BWFv0"),
    /** BWFv1-Tag – Broadcast Wave Format Version 1, erweitert um Peak-Level-Daten. */
    BWF_V1("BWFv1"),
    /** BWFv2-Tag – Broadcast Wave Format Version 2, aktuellste Version mit erweiterten Metadaten. */
    BWF_V2("BWFv2"),

    /** AIFF-Metadaten – Tag-Format für Audio Interchange File Format-Dateien. */
    AIFF_METADATA("AIFFMetadata"),

    /** Lyrics3v1-Tag – ältere Version des Lyrics3-Formats zum Einbetten von Liedtexten in MP3-Dateien. */
    LYRICS3V1("Lyrics3v1"),
    /** Lyrics3v2-Tag – erweiterte Version von Lyrics3 mit flexibleren Feldern für Liedtexte. */
    LYRICS3V2("Lyrics3v2"),

    /** ASF Content Description – grundlegender Metadatenblock in ASF/WMA/WMV-Dateien. */
    ASF_CONTENT_DESC("ASF Content Description"),
    /** ASF Extended Content Description – erweiterter Metadatenblock in ASF/WMA/WMV-Dateien mit zusätzlichen Attributen. */
    ASF_EXT_CONTENT_DESC("ASF Extended Content Description"),

    /** FLAC Application Block – anwendungsspezifische Metadatenblöcke in FLAC-Dateien. */
    FLAC_APPLICATION("FLAC Application"),

    /** Matroska Tags – Tag-Format für MKV/MKA/MKS-Containerdateien. */
    MATROSKA_TAGS("Matroska Tags"),
    /** WebM Tags – Tag-Format für WebM-Containerdateien. */
    WEBM_TAGS("WebM Tags"),

    /** DSF Metadata – Metadatenformat in DSD Stream File-Containern für High-End-Audio. */
    DSF_METADATA("DSF Metadata"),
    /** DFF Metadata – Metadatenformat in DSD Interchange File Format-Containern. */
    DFF_METADATA("DFF Metadata"),

    /** TTA Metadata – natives Metadatenformat in TrueAudio-Dateien. */
    TTA_METADATA("TTA Metadata"),

    /** WavPack Native – natives Metadatenformat in WavPack-Dateien. */
    WAVPACK_NATIVE("WavPack Native");

    /** Anzeigename des Tag-Formats. */
    private final String formatName;

    /**
     * Erstellt ein Tag-Format mit dem angegebenen Anzeigenamen.
     *
     * @param formatName der Anzeigename des Formats
     */
    TagFormat(String formatName) {
        this.formatName = formatName;
    }

    /**
     * Gibt den Anzeigenamen des Tag-Formats zurück.
     *
     * @return den Anzeigenamen als Zeichenkette
     */
    public String getFormatName() {
        return formatName;
    }
}