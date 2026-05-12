package com.schwanitz.strategies.parsing.id3;

import java.nio.charset.StandardCharsets;

/**
 * Parser für General Encapsulated Object Frames (GEOB / GEO).
 *
 * <p>General Object Frames enthalten ein eingebettetes binäres Objekt mit Metadaten:
 * MIME-Typ, Dateiname, Beschreibung und die eigentlichen Objektdaten.
 * Da die Objektdaten binär sind, wird nur die Größe ausgegeben.</p>
 *
 * <p>Der Aufbau ist: Kodierungsbyte | MIME-Typ (null-terminiert, ISO-8859-1) |
 * Dateiname (null-terminiert) | Beschreibung (null-terminiert) | Objektdaten.</p>
 */
public class GeneralObjectFrameParser implements ID3FrameParser {

    /**
     * Parst die Rohdaten eines General-Encapsulated-Object-Frames und gibt eine formatierte Information zurück.
     *
     * <p>Das Ergebnis hat das Format "[OBJECT:MIME-Typ, N bytes, file:Dateiname, desc:Beschreibung]",
     * wobei Dateiname und Beschreibung nur bei Vorhandensein angegeben werden.</p>
     *
     * @param data         Roh-Frame-Daten inklusive Kodierungsbyte
     * @param frameId      Die Frame-ID ("GEOB" für ID3v2.3/4 oder "GEO" für ID3v2.2)
     * @param majorVersion ID3v2 Hauptversion (2, 3 oder 4), verwendet für die Textdekodierung von Dateiname und Beschreibung
     * @return Die formatierte Objekt-Information, oder eine Fehlermeldung bei ungültigem MIME-Typ;
     *         ein leerer String bei zu kurzen Daten
     */
    @Override
    public String parse(byte[] data, String frameId, int majorVersion) {
        if (data.length < 2) {
            return "";
        }
        int encoding = data[0] & 0xFF;
        int pos = 1;

        int mimeEnd = ID3FrameParsingUtils.findNullTerminator(data, pos, ID3FrameParsingUtils.ISO_8859_1);
        if (mimeEnd == -1) {
            return "[OBJECT: Invalid MIME type]";
        }

        String mimeType = new String(data, pos, mimeEnd - pos, StandardCharsets.ISO_8859_1);
        pos = mimeEnd + 1;

        int filenameEnd = ID3FrameParsingUtils.findNullTerminator(data, pos, encoding);
        String filename = "";
        if (filenameEnd != -1) {
            byte[] filenameData = new byte[filenameEnd - pos];
            System.arraycopy(data, pos, filenameData, 0, filenameData.length);
            filename = ID3FrameParsingUtils.decodeText(filenameData, encoding, majorVersion);
            pos = filenameEnd + ID3FrameParsingUtils.getNullTerminatorSize(encoding);
        }

        int descEnd = ID3FrameParsingUtils.findNullTerminator(data, pos, encoding);
        String description = "";
        if (descEnd != -1) {
            byte[] descData = new byte[descEnd - pos];
            System.arraycopy(data, pos, descData, 0, descData.length);
            description = ID3FrameParsingUtils.decodeText(descData, encoding, majorVersion);
            pos = descEnd + ID3FrameParsingUtils.getNullTerminatorSize(encoding);
        }

        int objectDataSize = data.length - pos;
        String result = "[OBJECT:" + mimeType + "," + objectDataSize + " bytes";
        if (!filename.isEmpty()) {
            result += ",file:" + filename;
        }
        if (!description.isEmpty()) {
            result += ",desc:" + description;
        }
        result += "]";
        return result;
    }
}