package com.schwanitz.metadata;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.tagging.TagFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementierung der {@link Metadata}-Schnittstelle für das Vorbis-Comment-Tag-Format.
 * <p>
 * Diese Klasse hält eine Liste von {@link MetadataField}-Instanzen, die die
 * innerhalb eines Vorbis-Comment-Blocks gespeicherten Felder repräsentieren.
 * Das Tag-Format ist fest auf {@link TagFormat#VORBIS_COMMENT} eingestellt und
 * kann nicht frei gewählt werden, da VorbisComments kein versionsabhängiges
 * Format besitzen.
 * </p>
 */
public class VorbisMetadata implements Metadata {

    private final List<MetadataField<?>> fields = new ArrayList<>();

    /**
     * Gibt den Bezeichner des Vorbis-Comment-Tag-Formats zurück.
     *
     * @return das Bezeichnerkürzel des Tag-Formats ({@code "VorbisComment"}),
     *         niemals {@code null}
     */
    @Override
    public String getTagFormat() {
        return TagFormat.VORBIS_COMMENT.getFormatName();
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