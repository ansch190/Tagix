package com.schwanitz.tagging;

/**
 * Definiert die verschiedenen Scan-Modi für die Tag-Format-Erkennung.
 * <p>
 * Jeder Scan-Modus bestimmt, welche Tag-Formate geprüft werden und in welcher Reihenfolge:
 * <ul>
 *   <li>{@link #FULL_SCAN} – alle Formate, unabhängig von der Dateiendung</li>
 *   <li>{@link #COMFORT_SCAN} – nur wahrscheinliche Formate für den Dateityp</li>
 *   <li>{@link #CUSTOM_SCAN} – nur die vom Benutzer angegebenen Formate</li>
 * </ul>
 */
public enum ScanMode {
    /**
     * Vollständiger Scan: Alle Tag-Formate werden bei jeder Datei geprüft,
     * unabhängig von der Dateiendung. Reihenfolge nach allgemeiner Wahrscheinlichkeit.
     */
    FULL_SCAN,

    /**
     * Komfort-Scan: Nur wahrscheinliche Tag-Formate für die jeweilige Dateiendung
     * werden geprüft. Reihenfolge nach Wahrscheinlichkeit für den Dateityp.
     */
    COMFORT_SCAN,

    /**
     * Benutzerdefinierter Scan: Nur die vom Benutzer angegebenen Tag-Formate
     * werden geprüft, unabhängig von der Dateiendung.
     */
    CUSTOM_SCAN
}