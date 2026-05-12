package com.schwanitz.strategies.parsing.id3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gemeinsame Hilfsmethoden und Konstanten für ID3v2 Frame-Parser.
 */
public final class ID3FrameParsingUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ID3FrameParsingUtils.class);

    public static final int ISO_8859_1 = 0;
    public static final int UTF_16 = 1;
    public static final int UTF_16BE = 2;
    public static final int UTF_8 = 3;

    // Genre-Mapping für ID3v1 (Standard + Winamp Extension)
    public static final String[] ID3V1_GENRES = {
            "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge",
            "Hip-Hop", "Jazz", "Metal", "New Age", "Oldies", "Other", "Pop", "R&B",
            "Rap", "Reggae", "Rock", "Techno", "Industrial", "Alternative", "Ska",
            "Death Metal", "Pranks", "Soundtrack", "Euro-Techno", "Ambient",
            "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion", "Trance", "Classical",
            "Instrumental", "Acid", "House", "Game", "Sound Clip", "Gospel",
            "Noise", "Alternative Rock", "Bass", "Soul", "Punk", "Space",
            "Meditative", "Instrumental Pop", "Instrumental Rock", "Ethnic",
            "Gothic", "Darkwave", "Techno-Industrial", "Electronic", "Pop-Folk",
            "Eurodance", "Dream", "Southern Rock", "Comedy", "Cult", "Gangsta",
            "Top 40", "Christian Rap", "Pop/Funk", "Jungle", "Native US",
            "Cabaret", "New Wave", "Psychadelic", "Rave", "Showtunes", "Trailer",
            "Lo-Fi", "Tribal", "Acid Punk", "Acid Jazz", "Polka", "Retro",
            "Musical", "Rock & Roll", "Hard Rock",
            "Folk", "Folk-Rock", "National Folk", "Swing", "Fast Fusion",
            "Bebob", "Latin", "Revival", "Celtic", "Bluegrass", "Avantgarde",
            "Gothic Rock", "Progressive Rock", "Psychedelic Rock", "Symphonic Rock",
            "Slow Rock", "Big Band", "Chorus", "Easy Listening", "Acoustic",
            "Humour", "Speech", "Chanson", "Opera", "Chamber Music", "Sonata",
            "Symphony", "Booty Bass", "Primus", "Porn Groove", "Satire",
            "Slow Jam", "Club", "Tango", "Samba", "Folklore", "Ballad",
            "Power Ballad", "Rhythmic Soul", "Freestyle", "Duet", "Punk Rock",
            "Drum Solo", "A Capella", "Euro-House", "Dance Hall", "Goa",
            "Drum & Bass", "Club-House", "Hardcore", "Terror", "Indie",
            "BritPop", "Negerpunk", "Polsk Punk", "Beat", "Christian Gangsta Rap",
            "Heavy Metal", "Black Metal", "Crossover", "Contemporary Christian",
            "Christian Rock", "Merengue", "Salsa", "Thrash Metal", "Anime", "Jpop",
            "Synthpop", "Abstract", "Art Rock", "Baroque", "Bhangra", "Big Beat",
            "Breakbeat", "Chillout", "Downtempo", "Dub", "EBM", "Eclectic",
            "Electro", "Electroclash", "Emo", "Experimental", "Garage", "Global",
            "IDM", "Illbient", "Industro-Goth", "Jam Band", "Krautrock",
            "Leftfield", "Lounge", "Math Rock", "New Romantic", "Nu-Breakz",
            "Post-Punk", "Post-Rock", "Psytrance", "Shoegaze", "Space Rock",
            "Trop Rock", "World Music", "Neoclassical", "Audiobook", "Audio Theatre",
            "Neue Deutsche Welle", "Podcast", "Indie Rock", "G-Funk", "Dubstep",
            "Garage Rock", "Psybient"
    };

    private ID3FrameParsingUtils() {
        // Utility-Klasse
    }

    public static String parseGenre(String genre) {
        if (genre == null || genre.isEmpty()) {
            return genre;
        }

        List<String> parts = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\((\\d+)\\)");
        Matcher matcher = pattern.matcher(genre);

        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String text = genre.substring(lastEnd, matcher.start()).trim();
                if (!text.isEmpty()) {
                    parts.add(text);
                }
            }
            try {
                int genreNum = Integer.parseInt(matcher.group(1));
                if (genreNum >= 0 && genreNum < ID3V1_GENRES.length) {
                    parts.add(ID3V1_GENRES[genreNum]);
                }
            } catch (NumberFormatException e) {
                // ignore
            }
            lastEnd = matcher.end();
        }

        if (lastEnd < genre.length()) {
            String text = genre.substring(lastEnd).trim();
            if (!text.isEmpty()) {
                parts.add(text);
            }
        }

        if (parts.isEmpty()) {
            return genre;
        }

        return String.join("; ", new LinkedHashSet<>(parts));
    }

    public static String getPictureTypeDescription(int type) {
        return switch (type) {
            case 0 -> "Other";
            case 1 -> "32x32 file icon";
            case 2 -> "Other file icon";
            case 3 -> "Cover (front)";
            case 4 -> "Cover (back)";
            case 5 -> "Leaflet page";
            case 6 -> "Media";
            case 7 -> "Lead artist";
            case 8 -> "Artist";
            case 9 -> "Conductor";
            case 10 -> "Band";
            case 11 -> "Composer";
            case 12 -> "Lyricist";
            case 13 -> "Recording location";
            case 14 -> "During recording";
            case 15 -> "During performance";
            case 16 -> "Movie screen capture";
            case 17 -> "Bright colored fish";
            case 18 -> "Illustration";
            case 19 -> "Band logotype";
            case 20 -> "Publisher logotype";
            default -> "Unknown(" + type + ")";
        };
    }

    public static int findNullTerminator(byte[] data, int start, int encoding) {
        int nullSize = getNullTerminatorSize(encoding);
        for (int i = start; i <= data.length - nullSize; i++) {
            boolean isNull = true;
            for (int j = 0; j < nullSize; j++) {
                if (data[i + j] != 0) {
                    isNull = false;
                    break;
                }
            }
            if (isNull) {
                return i;
            }
        }
        return -1;
    }

    public static int getNullTerminatorSize(int encoding) {
        return switch (encoding) {
            case UTF_16, UTF_16BE -> 2;
            default -> 1;
        };
    }

    public static String decodeText(byte[] data, int encoding, int majorVersion) {
        if (data.length == 0) {
            return "";
        }

        Charset charset = switch (encoding) {
            case ISO_8859_1 -> StandardCharsets.ISO_8859_1;
            case UTF_16 -> StandardCharsets.UTF_16;
            case UTF_16BE -> StandardCharsets.UTF_16BE;
            case UTF_8 -> {
                if (majorVersion >= 4) {
                    yield StandardCharsets.UTF_8;
                }
                LOG.warn("UTF-8 encoding in ID3v2.{} - treating as ISO-8859-1", majorVersion);
                yield StandardCharsets.ISO_8859_1;
            }
            default -> {
                LOG.warn("Unknown text encoding: {} - using ISO-8859-1", encoding);
                yield StandardCharsets.ISO_8859_1;
            }
        };

        String result = new String(data, charset).trim();
        int nullPos = result.indexOf('\0');
        if (nullPos >= 0) {
            result = result.substring(0, nullPos);
        }
        return result;
    }

    public static String extractFixedString(byte[] data, int offset, int length) {
        if (offset + length > data.length) {
            return "";
        }
        int actualLength = length;
        for (int i = 0; i < length; i++) {
            if (data[offset + i] == 0) {
                actualLength = i;
                break;
            }
        }
        if (actualLength == 0) {
            return "";
        }
        return new String(data, offset, actualLength, StandardCharsets.ISO_8859_1).trim();
    }
}
