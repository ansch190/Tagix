package com.schwanitz.metadata;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.tagging.TagFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementierung der {@link Metadata}-Schnittstelle für das Lyrics3-Tag-Format.
 * <p>
 * Lyrics3 ist ein spezielles Tag-Format, das zum Speichern von Liedtexten
 * (Lyrics) in MP3-Dateien verwendet wird. Diese Klasse hält eine Liste von
 * {@link MetadataField}-Instanzen, die die innerhalb eines Lyrics3-Tags
 * gespeicherten Felder repräsentieren. Das konkrete Lyrics3-Format wird über
 * den Konstruktor durch einen {@link TagFormat}-Wert festgelegt.
 * </p>
 */
public class Lyrics3Metadata implements Metadata {

    private final List<MetadataField<?>> fields = new ArrayList<>();

    private final TagFormat format;

    /**
     * Erzeugt eine neue Lyrics3-Metadaten-Instanz für das angegebene Tag-Format.
     *
     * @param format das spezifische Lyrics3-Tag-Format,
     *                darf nicht {@code null} sein
     */
    public Lyrics3Metadata(TagFormat format) {
        this.format = format;
    }

    /**
     * Gibt den Bezeichner des durch diese Instanz repräsentierten Lyrics3-Tag-Formats zurück.
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