package com.schwanitz.utils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;

/**
 * Utility-Klasse für Encoding-Fallback-Logik.
 *
 * <p>Die drei Parsing-Strategien (AIFF, RIFF, Lyrics3) verwenden alle
 * ein "try Encoding → catch → next"-Pattern mit unterschiedlichen
 * Fallback-Reihenfolgen und Validierungs-Heuristiken. Diese Klasse
 * bündelt die gemeinsame Logik.
 */
public final class EncodingUtils {

    /** ASCII printable range helpers */
    private static final char ASCII_PRINTABLE_MIN = 32;
    private static final char TAB = 9;
    private static final char LF = 10;
    private static final char CR = 13;

    private EncodingUtils() {
    }

    /**
     * Versucht mehrere Encodings in Reihenfolge, validiert mit dem gegebenen Predicate.
     * Gibt das erste gültige Ergebnis zurück. Das letzte Encoding wird immer akzeptiert
     * (Last Resort).
     *
     * @param data          die zu decodierenden Rohdaten
     * @param offset        Startoffset im Array
     * @param length        Anzahl der Bytes
     * @param fallbackOrder Encodings in Reihenfolge der Priorität
     * @param validator     Validierung für decodierte Strings (null = keine Validierung)
     * @return der decodierte String
     */
    public static String decodeWithFallback(
            byte[] data,
            int offset,
            int length,
            Charset[] fallbackOrder,
            Predicate<String> validator) {

        for (int i = 0; i < fallbackOrder.length; i++) {
            try {
                String decoded = new String(data, offset, length, fallbackOrder[i]).trim();

                if (i == fallbackOrder.length - 1) {
                    return decoded;
                }

                if (validator == null || validator.test(decoded)) {
                    return decoded;
                }
            } catch (Exception e) {
                // Nächstes Encoding versuchen
            }
        }

        return new String(data, offset, length, fallbackOrder[fallbackOrder.length - 1]).trim();
    }

    /**
     * Überladung ohne offset/length (ganzes Array).
     */
    public static String decodeWithFallback(
            byte[] data,
            Charset[] fallbackOrder,
            Predicate<String> validator) {
        return decodeWithFallback(data, 0, data.length, fallbackOrder, validator);
    }

    /**
     * Geteilte Validierung: Keine Control-Chars außer Tab (9), LF (10), CR (13).
     * Wird von AIFFMetadataParsingStrategy und RIFFInfoParsingStrategy verwendet.
     */
    public static boolean isValidText(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < ASCII_PRINTABLE_MIN && c != TAB && c != LF && c != CR) {
                return false;
            }
        }
        return true;
    }
}
