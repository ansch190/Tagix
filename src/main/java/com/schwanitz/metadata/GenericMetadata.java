package com.schwanitz.metadata;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.tagging.TagFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * Generische Implementierung der {@link Metadata}-Schnittstelle für alle Audio-Tag-Formate.
 * <p>
 * Diese Klasse ersetzt die zahlreichen format-spezifischen inneren Klassen in den
 * Parsing-Strategien. Sie hält eine Liste von {@link MetadataField}-Instanzen und
 * delegiert {@link #getTagFormat()} an den übergebenen {@link TagFormat}-Wert.
 * </p>
 *
 * @see Metadata
 * @see TagFormat
 */
public class GenericMetadata implements Metadata {

    private final List<MetadataField<?>> fields = new ArrayList<>();
    private final TagFormat format;

    /**
     * Erzeugt eine neue generische Metadaten-Instanz für das angegebene Tag-Format.
     *
     * @param format das spezifische Tag-Format (z.&nbsp;B. {@link TagFormat#ID3V2_4}),
     *               darf nicht {@code null} sein
     */
    public GenericMetadata(TagFormat format) {
        this.format = format;
    }

    /**
     * Gibt den Bezeichner des durch diese Instanz repräsentierten Tag-Formats zurück.
     *
     * @return das Bezeichnerkürzel des Tag-Formats, niemals {@code null}
     */
    @Override
    public String getTagFormat() {
        return format.getFormatName();
    }

    /**
     * Gibt die Liste aller in dieser Instanz gespeicherten Metadatenfelder zurück.
     *
     * @return die Liste der Metadatenfelder, niemals {@code null}
     */
    @Override
    public List<MetadataField<?>> getFields() {
        return fields;
    }

    /**
     * Fügt der Liste der Metadatenfelder ein neues Feld hinzu.
     *
     * @param field das hinzuzufügende Metadatenfeld, darf nicht {@code null} sein
     * @throws IllegalArgumentException wenn {@code field} {@code null} ist
     */
    @Override
    public void addField(MetadataField<?> field) {
        fields.add(field);
    }
}
