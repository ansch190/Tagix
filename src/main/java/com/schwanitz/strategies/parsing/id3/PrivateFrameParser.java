package com.schwanitz.strategies.parsing.id3;

import java.nio.charset.StandardCharsets;

/**
 * Parser für Private Frames (PRIV).
 *
 * <p>Private Frames enthalten einen Eigentümer-Bezeichner (Owner Identifier)
 * gefolgt von privaten Binärdaten. Da der Inhalt der privaten Daten nicht
 * standardisiert ist, wird nur die Größe der privaten Daten ausgegeben.</p>
 *
 * <p>Der Aufbau ist: Owner-Identifier (null-terminiert, ISO-8859-1) | private Daten.</p>
 */
public class PrivateFrameParser implements ID3FrameParser {

    /**
     * Parst die Rohdaten eines Private-Frames und gibt eine formatierte Information zurück.
     *
     * <p>Das Ergebnis hat das Format "[PRIVATE:Owner, N bytes]", wobei Owner der
     * Eigentümer-Bezeichner ist und N die Anzahl der privaten Datenbytes.</p>
     *
     * @param data         Roh-Frame-Daten (Owner-Identifier null-terminiert, gefolgt von privaten Daten)
     * @param frameId      Die Frame-ID ("PRIV")
     * @param majorVersion ID3v2 Hauptversion (2, 3 oder 4)
     * @return Die formatierte private Frame-Information, oder ein leerer String bei leeren Daten
     */
    @Override
    public String parse(byte[] data, String frameId, int majorVersion) {
        if (data.length == 0) {
            return "";
        }
        int ownerEnd = ID3FrameParsingUtils.findNullTerminator(data, 0, ID3FrameParsingUtils.ISO_8859_1);
        if (ownerEnd == -1) {
            return "[PRIVATE:" + data.length + " bytes]";
        }

        String owner = new String(data, 0, ownerEnd, StandardCharsets.ISO_8859_1);
        int dataSize = data.length - ownerEnd - 1;
        return "[PRIVATE:" + owner + "," + dataSize + " bytes]";
    }
}