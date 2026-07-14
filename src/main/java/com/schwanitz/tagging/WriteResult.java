package com.schwanitz.tagging;

import java.util.Objects;

/**
 * Ergebnis eines Tag-Schreibvorgangs.
 *
 * @param success    {@code true} wenn der Schreibvorgang erfolgreich war
 * @param format     das betroffene Tag-Format
 * @param oldTagSize die Größe des alten Tags in Bytes (0 wenn kein bestehendes Tag)
 * @param newTagSize die Größe des neuen Tags in Bytes (0 bei REMOVE)
 * @param message    optionale Meldung über den Ausgang
 */
public record WriteResult(
        boolean success,
        TagFormat format,
        long oldTagSize,
        long newTagSize,
        String message
) {

    /**
     * Erstellt ein Erfolgsergebnis.
     *
     * @param format  das betroffene Tag-Format
     * @param oldSize die Größe des alten Tags
     * @param newSize die Größe des neuen Tags
     * @return ein Erfolgsergebnis
     */
    public static WriteResult success(TagFormat format, long oldSize, long newSize) {
        return new WriteResult(true, format, oldSize, newSize, "Erfolgreich geschrieben");
    }

    /**
     * Erstellt ein Fehlerergebnis.
     *
     * @param format  das betroffene Tag-Format
     * @param message die Fehlermeldung
     * @return ein Fehlerergebnis
     */
    public static WriteResult failure(TagFormat format, String message) {
        return new WriteResult(false, format, 0, 0, message);
    }

    /**
     * Erstellt ein Erfolgsergebnis mit benutzerdefinierter Meldung.
     *
     * @param format  das betroffene Tag-Format
     * @param oldSize die Größe des alten Tags
     * @param newSize die Größe des neuen Tags
     * @param message die Erfolgsmeldung
     * @return ein Erfolgsergebnis
     */
    public static WriteResult success(TagFormat format, long oldSize, long newSize, String message) {
        return new WriteResult(true, format, oldSize, newSize, message);
    }
}
