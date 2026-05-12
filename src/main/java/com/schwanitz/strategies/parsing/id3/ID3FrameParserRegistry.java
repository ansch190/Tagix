package com.schwanitz.strategies.parsing.id3;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry die Frame-IDs auf spezialisierte ID3FrameParser mapped.
 *
 * <p>Die Registry unterstützt zwei Arten von Zuordnungen: exakte Übereinstimmungen
 * für spezifische Frame-IDs (z.B. "TXXX", "APIC") und Präfix-Matches für
 * Frame-Familien (z.B. alle Frames die mit "T" beginnen).</p>
 *
 * <p>Bei der Suche wird zuerst die exakte Übereinstimmung geprüft, dann
 * die Präfix-Matches. Dadurch können spezifische Parser (wie TXXX) Vorrang
 * vor dem generischen Text-Parser haben.</p>
 */
public class ID3FrameParserRegistry {

    private final Map<String, ID3FrameParser> exactParsers = new HashMap<>();
    private final Map<String, ID3FrameParser> prefixParsers = new HashMap<>();

    /**
     * Erstellt eine neue Registry und registriert die Standard-Parser für alle bekannten ID3v2-Frames.
     */
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
     *
     * @param frameId Die Frame-ID (z.B. "TIT2", "APIC", "WCOM")
     * @return Der zugehörige ID3FrameParser, oder {@code null} wenn kein passender Parser gefunden wurde
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

    /**
     * Registriert einen Parser für eine exakte Frame-ID.
     *
     * <p>Ein bereits für diese Frame-ID registrierter Parser wird überschrieben.</p>
     *
     * @param frameId Die exakte Frame-ID, für die der Parser gelten soll (z.B. "TXXX")
     * @param parser  Der Parser, der für diese Frame-ID verwendet werden soll
     */
    public void registerExact(String frameId, ID3FrameParser parser) {
        exactParsers.put(frameId, parser);
    }
}