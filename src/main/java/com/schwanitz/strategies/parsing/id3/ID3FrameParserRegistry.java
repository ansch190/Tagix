package com.schwanitz.strategies.parsing.id3;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry die Frame-IDs auf spezialisierte ID3FrameParser mapped.
 */
public class ID3FrameParserRegistry {

    private final Map<String, ID3FrameParser> exactParsers = new HashMap<>();
    private final Map<String, ID3FrameParser> prefixParsers = new HashMap<>();

    public ID3FrameParserRegistry() {
        registerDefaults();
    }

    private void registerDefaults() {
        // User Defined Text
        exactParsers.put("TXXX", new UserDefinedTextFrameParser());
        exactParsers.put("TXX", new UserDefinedTextFrameParser());

        // Comments
        exactParsers.put("COMM", new CommentFrameParser());
        exactParsers.put("COM", new CommentFrameParser());

        // Lyrics
        exactParsers.put("USLT", new LyricsFrameParser());
        exactParsers.put("ULT", new LyricsFrameParser());

        // Picture
        exactParsers.put("APIC", new PictureFrameParser());
        exactParsers.put("PIC", new PictureFrameParser());

        // Play Counter
        exactParsers.put("PCNT", new PlayCounterFrameParser());
        exactParsers.put("CNT", new PlayCounterFrameParser());

        // Popularimeter
        exactParsers.put("POPM", new PopularimeterFrameParser());
        exactParsers.put("POP", new PopularimeterFrameParser());

        // General Encapsulated Object
        exactParsers.put("GEOB", new GeneralObjectFrameParser());
        exactParsers.put("GEO", new GeneralObjectFrameParser());

        // Private
        exactParsers.put("PRIV", new PrivateFrameParser());

        // URL Frames (alle die mit W beginnen)
        prefixParsers.put("W", new UrlFrameParser());

        // Text Frames (alle die mit T beginnen, TXXX/TXX sind bereits als exact registriert)
        prefixParsers.put("T", new TextFrameParser());
    }

    /**
     * Gibt den passenden Parser für eine Frame-ID zurück.
     * Prüft zuerst exakte Übereinstimmungen, dann Präfix-Matches.
     */
    public ID3FrameParser getParser(String frameId) {
        ID3FrameParser exact = exactParsers.get(frameId);
        if (exact != null) {
            return exact;
        }

        for (Map.Entry<String, ID3FrameParser> entry : prefixParsers.entrySet()) {
            if (frameId.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    public void registerExact(String frameId, ID3FrameParser parser) {
        exactParsers.put(frameId, parser);
    }
}
