package com.schwanitz.metadata;

import com.schwanitz.interfaces.FieldHandler;
import java.nio.charset.StandardCharsets;

/**
 * Ein {@link FieldHandler} für textbasierte Metadatenfelder, der UTF-8 als Zeichenkodierung verwendet.
 * <p>
 * Diese Implementierung wandelt rohe Binärdaten mithilfe von UTF-8 in einen
 * {@link String} um und serialisiert Zeichenketten entsprechend zurück in
 * Byte-Arrays. Sie eignet sich für alle Textfelder, deren Binärdarstellung
 * der UTF-8-Kodierung folgt.
 * </p>
 */
public class TextFieldHandler implements FieldHandler<String> {

    private final String key;

    /**
     * Erzeugt einen neuen Textfeld-Handler für den angegebenen Schlüssel.
     *
     * @param key der eindeutige Schlüssel des Felds, darf nicht {@code null} sein
     */
    public TextFieldHandler(String key) {
        this.key = key;
    }

    /**
     * Gibt den eindeutigen Schlüssel des Felds zurück.
     *
     * @return der Schlüssel des Felds, niemals {@code null}
     */
    @Override
    public String getKey() {
        return key;
    }

    /**
     * Wandelt die übergebenen Binärdaten in einen UTF-8-kodierten String um.
     *
     * @param data die rohen Binärdaten des Felds, darf nicht {@code null} sein
     * @return der aus den Binärdaten erzeugte String, niemals {@code null}
     * @throws IllegalArgumentException wenn {@code data} {@code null} ist
     */
    @Override
    public String readData(byte[] data) {
        return new String(data, StandardCharsets.UTF_8);
    }

    /**
     * Wandelt den angegebenen String in ein UTF-8-kodiertes Byte-Array um.
     *
     * @param value der zu serialisierende String, darf nicht {@code null} sein
     * @return das UTF-8-kodierte Byte-Array, niemals {@code null}
     * @throws IllegalArgumentException wenn {@code value} {@code null} ist
     */
    @Override
    public byte[] writeData(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}