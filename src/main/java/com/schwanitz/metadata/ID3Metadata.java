package com.schwanitz.metadata;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.tagging.TagFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementierung der {@link Metadata}-Schnittstelle für ID3-Tag-Formate.
 * <p>
 * Diese Klasse hält eine Liste von {@link MetadataField}-Instanzen, die die
 * innerhalb eines ID3-Tags (beispielsweise ID3v2.3 oder ID3v2.4) gespeicherten
 * Felder repräsentieren. Das spezifische Tag-Format wird über den Konstruktor
 * durch einen {@link TagFormat}-Wert festgelegt.
 * </p>
 */
public class ID3Metadata implements Metadata {

    private final List<MetadataField<?>> fields = new ArrayList<>();

    private final TagFormat format;

    /**
     * Erzeugt eine neue ID3-Metadaten-Instanz für das angegebene Tag-Format.
     *
     * @param format das spezifische ID3-Tag-Format (z.&nbsp;B. ID3v2.3 oder ID3v2.4),
     *               darf nicht {@code null} sein
     */
    public ID3Metadata(TagFormat format) {
        this.format = format;
    }

    /**
     * Gibt den Bezeichner des durch diese Instanz repräsentierten ID3-Tag-Formats zurück.
     *
     * @return das Bezeichnerkürzel des Tag-Formats (z.&nbsp;B. {@code "ID3v2.4"}),
     *         niemals {@code null}
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