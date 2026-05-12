package com.schwanitz.interfaces;

/**
 * Schnittstelle für die Konvertierung zwischen rohen Binärdaten und typisierten Feldwerten.
 * <p>
 * Ein {@code FieldHandler} verantwortet die Serialisierung und Deserialisierung
 * eines einzelnen Metadatenfelds. Jeder Handler ist einem eindeutigen Schlüssel
 * zugeordnet und übernimmt die formatspezifische Umwandlung zwischen dem
 * Byte-Array aus der Datei und dem gewünschten Java-Typ {@code T}.
 * </p>
 *
 * @param <T> der Java-Typ, in den die Binärdaten des Felds überführt werden
 */
public interface FieldHandler<T> {

    /**
     * Gibt den eindeutigen Schlüssel des Felds zurück.
     * <p>
     * Der Schlüssel identifiziert das Feld innerhalb des Tag-Formats eindeutig,
     * beispielsweise als Framespezifikation bei ID3v2 oder als Schlüsselwort
     * bei VorbisComment.
     * </p>
     *
     * @return der eindeutige Schlüssel des Felds, niemals {@code null}
     */
    String getKey();

    /**
     * Wandelt die übergebenen Binärdaten in einen typisierten Wert um.
     * <p>
     * Die Interpretation des Byte-Arrays richtet sich nach dem jeweiligen
     * Tag-Format und dem durch {@link #getKey()} identifizierten Feldtyp.
     * </p>
     *
     * @param data die rohen Binärdaten des Felds, darf nicht {@code null} sein
     * @return der deserialisierte Wert, niemals {@code null}
     * @throws IllegalArgumentException wenn {@code data} {@code null} ist oder
     *         die Daten nicht gelesen werden können
     */
    T readData(byte[] data);

    /**
     * Wandelt den typisierten Wert in ein Byte-Array um, das in die Medien-
     * datei geschrieben werden kann.
     * <p>
     * Das zurückgegebene Byte-Array muss dem erwarteten Binärformat des
     * jeweiligen Tag-Formats entsprechen.
     * </p>
     *
     * @param value der zu serialisierende Wert, darf nicht {@code null} sein
     * @return das serialisierte Byte-Array, niemals {@code null}
     * @throws IllegalArgumentException wenn {@code value} {@code null} ist oder
     *         nicht serialisiert werden kann
     */
    byte[] writeData(T value);
}