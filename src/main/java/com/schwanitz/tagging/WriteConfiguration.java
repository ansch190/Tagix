package com.schwanitz.tagging;

import java.util.Objects;

/**
 * Konfiguration für Tag-Schreibvorgänge.
 *
 * @param mode                der Schreibmodus
 * @param inPlace             {@code true} für In-Place-Schreiben wenn möglich
 * @param id3Version          die bevorzugte ID3v2-Version (3 oder 4)
 * @param encoding            die Zeichenkodierung (z.B. "UTF-8", "ISO-8859-1")
 * @param preserveExistingTags {@code true} um bestehende Tags zu behalten
 */
public record WriteConfiguration(
        WriteMode mode,
        boolean inPlace,
        int id3Version,
        String encoding,
        boolean preserveExistingTags
) {

    /**
     * Erstellt eine Standard-Konfiguration.
     * <p>
     * Einstellungen: UPDATE_EXISTING, kein In-Place, ID3v2.4, UTF-8, bestehende Tags beibehalten.
     * </p>
     *
     * @return eine neue Standard-Konfiguration
     */
    public static WriteConfiguration defaults() {
        return new WriteConfiguration(WriteMode.UPDATE_EXISTING, false, 4, "UTF-8", true);
    }

    /**
     * Erstellt eine Konfiguration für In-Place-Schreiben.
     *
     * @return eine neue In-Place-Konfiguration mit ID3v2.4
     */
    public static WriteConfiguration forInPlace() {
        return new WriteConfiguration(WriteMode.UPDATE_EXISTING, true, 4, "UTF-8", true);
    }

    /**
     * Erstellt eine Konfiguration für In-Place-Schreiben mit ID3v2.3.
     *
     * @return eine neue In-Place-Konfiguration mit ID3v2.3
     */
    public static WriteConfiguration inPlaceId3v23() {
        return new WriteConfiguration(WriteMode.UPDATE_EXISTING, true, 3, "UTF-8", true);
    }

    /**
     * Erstellt eine Konfiguration für In-Place-Schreiben mit ID3v2.4.
     *
     * @return eine neue In-Place-Konfiguration mit ID3v2.4
     */
    public static WriteConfiguration inPlaceId3v24() {
        return new WriteConfiguration(WriteMode.UPDATE_EXISTING, true, 4, "UTF-8", true);
    }

    /**
     * Erstellt eine Konfiguration zum vollständigen Ersetzen aller Tags.
     *
     * @return eine neue REPLACE-ALL-Konfiguration
     */
    public static WriteConfiguration replaceAll() {
        return new WriteConfiguration(WriteMode.REPLACE_ALL, false, 4, "UTF-8", false);
    }

    /**
     * Erstellt eine Konfiguration zum Entfernen von Tags.
     *
     * @return eine neue REMOVE-Konfiguration
     */
    public static WriteConfiguration remove() {
        return new WriteConfiguration(WriteMode.REMOVE, false, 4, "UTF-8", false);
    }

    /**
     * Erstellt eine Konfiguration für das Erstellen neuer Tags.
     *
     * @return eine neue CREATE_NEW-Konfiguration
     */
    public static WriteConfiguration createNew() {
        return new WriteConfiguration(WriteMode.CREATE_NEW, false, 4, "UTF-8", false);
    }
}
