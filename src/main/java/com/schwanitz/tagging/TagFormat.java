package com.schwanitz.tagging;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public enum TagFormat {

    ID3V1("ID3v1", "mp3", "wav"),
    ID3V1_1("ID3v1.1", "mp3", "wav"),
    ID3V2_2("ID3v2.2", "mp3", "wav"),
    ID3V2_3("ID3v2.3", "mp3", "wav"),
    ID3V2_4("ID3v2.4", "mp3", "wav"),
    APEV1("APEv1", "mp3", "ape", "wv", "mpc"),
    APEV2("APEv2", "mp3", "ape", "wv", "mpc"),
    VORBIS_COMMENT("VorbisComment", "ogg", "flac", "spx", "opus"),
    MP4("MP4", "m4a", "mp4", "m4v"),
    RIFF_INFO("RIFFInfo", "wav", "avi"),
    BWF_V0("BWFv0", "wav"),
    BWF_V1("BWFv1", "wav"),
    BWF_V2("BWFv2", "wav"),
    AIFF_METADATA("AIFFMetadata", "aiff", "aif"),
    LYRICS3V1("Lyrics3v1", "mp3"),
    LYRICS3V2("Lyrics3v2", "mp3");

    private final String formatName;
    private final Set<String> fileExtensions;

    TagFormat(String formatName, String... fileExtensions) {
        this.formatName = formatName;
        this.fileExtensions = new HashSet<>(Arrays.asList(fileExtensions));
    }

    public String getFormatName() {
        return formatName;
    }

    public Set<String> getFileExtensions() {
        return fileExtensions;
    }

    public static TagFormat fromFormatName(String formatName) {
        for (TagFormat format : values()) {
            if (format.formatName.equals(formatName)) {
                return format;
            }
        }
        return null;
    }
}