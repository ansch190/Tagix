package com.schwanitz.strategies.parsing.id3;

/**
 * Parser für User-Defined Text Frames (TXXX / TXX).
 *
 * <p>TXXX-Frames enthalten eine Beschreibung und einen Wert, getrennt durch einen
 * Null-Terminator. Dieser Parser extrahiert beide Teile und gibt sie im Format
 * "Beschreibung: Wert" zurück. Wenn die Beschreibung leer ist, wird nur der Wert zurückgegeben.</p>
 */
public class UserDefinedTextFrameParser implements ID3FrameParser {

    /**
     * Parst die Rohdaten eines TXXX/TXX-Frames und gibt Beschreibung und Wert zurück.
     *
     * <p>Der Aufbau ist: Kodierungsbyte | Beschreibung (null-terminiert) | Wert.
     * Wenn eine Beschreibung vorhanden ist, wird das Format "Beschreibung: Wert" zurückgegeben,
     * andernfalls nur der Wert.</p>
     *
     * @param data         Roh-Frame-Daten inklusive Kodierungsbyte
     * @param frameId      Die Frame-ID ("TXXX" oder "TXX")
     * @param majorVersion ID3v2 Hauptversion (2, 3 oder 4)
     * @return Der formatierte String aus Beschreibung und Wert, oder ein leerer String wenn die Daten ungültig sind
     */
    @Override
    public String parse(byte[] data, String frameId, int majorVersion) {
        if (data.length < 2) {
            return "";
        }
        int encoding = data[0] & 0xFF;
        int descEnd = ID3FrameParsingUtils.findNullTerminator(data, 1, encoding);
        if (descEnd == -1) {
            return "";
        }

        byte[] descData = new byte[descEnd - 1];
        System.arraycopy(data, 1, descData, 0, descData.length);
        String description = ID3FrameParsingUtils.decodeText(descData, encoding, majorVersion);

        int valueStart = descEnd + ID3FrameParsingUtils.getNullTerminatorSize(encoding);
        if (valueStart >= data.length) {
            return description;
        }

        byte[] valueData = new byte[data.length - valueStart];
        System.arraycopy(data, valueStart, valueData, 0, valueData.length);
        String value = ID3FrameParsingUtils.decodeText(valueData, encoding, majorVersion);

        return description.isEmpty() ? value : description + ": " + value;
    }
}