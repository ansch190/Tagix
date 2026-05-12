package com.schwanitz.strategies.parsing.id3;

/**
 * Interface für spezialisierte ID3v2 Frame-Parser.
 */
@FunctionalInterface
public interface ID3FrameParser {

    /**
     * Parst die Rohdaten eines Frames und gibt einen String-Wert zurück.
     *
     * @param data         Roh-Frame-Daten (ohne Header)
     * @param frameId      Die Frame-ID (z.B. "TIT2", "APIC")
     * @param majorVersion ID3v2 Hauptversion (2, 3 oder 4)
     * @return Der geparste Wert als String, oder leerer String wenn nicht parsbar
     */
    String parse(byte[] data, String frameId, int majorVersion);
}
