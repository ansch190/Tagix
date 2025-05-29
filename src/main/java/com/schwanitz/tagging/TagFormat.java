package com.schwanitz.tagging;

public enum TagFormat {

    // ID3 Formate
    ID3V1("ID3v1"),
    ID3V1_1("ID3v1.1"),
    ID3V2_2("ID3v2.2"),
    ID3V2_3("ID3v2.3"),
    ID3V2_4("ID3v2.4"),

    // APE Formate
    APEV1("APEv1"),
    APEV2("APEv2"),

    // Vorbis Comments
    VORBIS_COMMENT("VorbisComment"),

    // MP4/iTunes
    MP4("MP4"),

    // RIFF/WAV Formate
    RIFF_INFO("RIFFInfo"),
    BWF_V0("BWFv0"),
    BWF_V1("BWFv1"),
    BWF_V2("BWFv2"),

    // AIFF
    AIFF_METADATA("AIFFMetadata"),

    // Lyrics
    LYRICS3V1("Lyrics3v1"),
    LYRICS3V2("Lyrics3v2"),

    // ASF/WMA Formate (NEU)
    ASF_CONTENT_DESC("ASF Content Description"),
    ASF_EXT_CONTENT_DESC("ASF Extended Content Description"),

    // FLAC Application Blocks (NEU)
    FLAC_APPLICATION("FLAC Application"),

    // Matroska/WebM (NEU)
    MATROSKA_TAGS("Matroska Tags"),
    WEBM_TAGS("WebM Tags"),

    // DSD Formate (NEU)
    DSF_METADATA("DSF Metadata"),
    DFF_METADATA("DFF Metadata"),

    // TrueAudio (NEU)
    TTA_METADATA("TTA Metadata"),

    // WavPack Native (NEU)
    WAVPACK_NATIVE("WavPack Native");

    private final String formatName;

    TagFormat(String formatName) {
        this.formatName = formatName;
    }

    public String getFormatName() {
        return formatName;
    }
}