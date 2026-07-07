package com.schwanitz.interfaces;

import com.schwanitz.metadata.MetadataField;
import com.schwanitz.metadata.PictureData;
import java.util.ArrayList;
import java.util.List;

/**
 * Hauptschnittstelle für den Zugriff auf Metadaten eines Audio- oder Medienfiles.
 * <p>
 * Diese Schnittstelle stellt die grundlegenden Operationen bereit, um das Tag-Format
 * zu identifizieren, vorhandene Metadatenfelder abzufragen und neue Felder hinzuzufügen.
 * Implementierende Klassen kapseln dabei die formatspezifische Darstellung der Metadaten.
 * </p>
 */
public interface Metadata {

    /**
     * Gibt das Bezeichnerkürzel des Tag-Formats zurück.
     * <p>
 * Beispiele für mögliche Rückgabewerte sind {@code "ID3v2.4"}, {@code "VorbisComment"}
     * oder {@code "MP4"}.
     * </p>
     *
     * @return das Bezeichnerkürzel des Tag-Formats, niemals {@code null}
     */
    String getTagFormat();

    /**
     * Gibt eine unveränderliche Liste aller aktuell gespeicherten Metadatenfelder zurück.
     * <p>
     * Die Reihenfolge der Felder richtet sich nach der Implementierung und kann
     * der ursprünglichen Reihenfolge im Dateiformat entsprechen.
     * </p>
     *
     * @return die Liste der Metadatenfelder, niemals {@code null}
     */
    List<MetadataField<?>> getFields();

    /**
     * Fügt ein neues Metadatenfeld hinzu.
     * <p>
     * Wenn bereits ein Feld mit demselben Schlüssel existiert, ist das Verhalten
     * implementierungsabhängig – es kann das bestehende Feld ersetzt oder
     * ein zusätzliches Feld eingefügt werden.
     * </p>
     *
     * @param field das hinzuzufügende Metadatenfeld, darf nicht {@code null} sein
     * @throws IllegalArgumentException wenn {@code field} {@code null} ist
     */
    void addField(MetadataField<?> field);

    default List<PictureData> getPictures() {
        List<PictureData> pictures = new ArrayList<>();
        for (MetadataField<?> field : getFields()) {
            if (field.getValue() instanceof PictureData pd) {
                pictures.add(pd);
            }
        }
        return pictures;
    }
}