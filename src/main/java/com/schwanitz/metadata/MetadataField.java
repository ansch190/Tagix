package com.schwanitz.metadata;

import com.schwanitz.interfaces.FieldHandler;

/**
 * Repräsentiert ein einzelnes Metadatenfeld bestehend aus Schlüssel, Wert und zugehörigem Handler.
 * <p>
 * Ein {@code MetadataField} kapselt einen typisierten Wert {@code T} zusammen mit dem
 * Schlüssel, der das Feld innerhalb des Tag-Formats identifiziert, und einem
 * {@link FieldHandler}, der für die Serialisierung und Deserialisierung des Werts
 * zuständig ist. Diese Klasse dient als universeller Container, der von den
 * verschiedenen Metadaten-Implementierungen verwendet wird, um Felder einheitlich
 * zu verwalten.
 * </p>
 *
 * @param <T> der Java-Typ des Feldwerts
 */
public class MetadataField<T> {

    private final String key;
    private T value;
    private final FieldHandler<T> handler;

    /**
     * Erzeugt ein neues Metadatenfeld mit dem angegebenen Schlüssel, Wert und Handler.
     *
     * @param key     der eindeutige Schlüssel des Felds, darf nicht {@code null} sein
     * @param value   der anfängliche Wert des Felds
     * @param handler der Handler für die Serialisierung und Deserialisierung des Werts,
     *                darf nicht {@code null} sein
     */
    public MetadataField(String key, T value, FieldHandler<T> handler) {
        this.key = key;
        this.value = value;
        this.handler = handler;
    }

    /**
     * Gibt den eindeutigen Schlüssel des Felds zurück.
     * <p>
     * Der Schlüssel identifiziert das Feld innerhalb des jeweiligen Tag-Formats,
     * beispielsweise als Frame-ID bei ID3v2 oder als Schlüsselwort bei VorbisComment.
     * </p>
     *
     * @return der Schlüssel des Felds, niemals {@code null}
     */
    public String getKey() {
        return key;
    }

    /**
     * Gibt den aktuellen Wert des Felds zurück.
     *
     * @return der Wert des Felds
     */
    public T getValue() {
        return value;
    }

    /**
     * Setzt den Wert des Felds auf den angegebenen Wert.
     *
     * @param value der neue Wert des Felds
     */
    public void setValue(T value) {
        this.value = value;
    }

    /**
     * Gibt den mit diesem Feld verknüpften Handler zurück.
     * <p>
     * Der Handler ermöglicht die Konvertierung zwischen dem typisierten Java-Wert
     * und der binären Darstellung im jeweiligen Tag-Format.
     * </p>
     *
     * @return der zugehörige {@link FieldHandler}, niemals {@code null}
     */
    public FieldHandler<T> getHandler() {
        return handler;
    }
}