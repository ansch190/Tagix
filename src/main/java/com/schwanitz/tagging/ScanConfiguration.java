package com.schwanitz.tagging;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unveränderliche Konfigurationsklasse für Scan-Modi der Tag-Format-Erkennung.
 * <p>
 * Kapselt einen {@link ScanMode} und optional eine Liste von benutzerdefinierten
 * Tag-Formaten. Instanzen werden über die statischen Fabrikmethoden erstellt.
 *
 * @see #fullScan()
 * @see #comfortScan()
 * @see #customScan(TagFormat...)
 */
public class ScanConfiguration {

    /** Der aktive Scan-Modus. */
    private final ScanMode mode;

    /** Die Liste der benutzerdefinierten Tag-Formate (leer bei FULL_SCAN und COMFORT_SCAN). */
    private final List<TagFormat> customFormats;

    private ScanConfiguration(ScanMode mode, List<TagFormat> customFormats) {
        this.mode = mode;
        this.customFormats = customFormats != null ?
                List.copyOf(customFormats) :
                Collections.emptyList();
    }

    /**
     * Erstellt eine Konfiguration für den vollständigen Scan.
     * <p>
     * Alle bekannten Tag-Formate werden geprüft, unabhängig von der Dateiendung.
     *
     * @return eine Scan-Konfiguration mit {@link ScanMode#FULL_SCAN}
     */
    public static ScanConfiguration fullScan() {
        return new ScanConfiguration(ScanMode.FULL_SCAN, null);
    }

    /**
     * Erstellt eine Konfiguration für den Komfort-Scan.
     * <p>
     * Nur die für die jeweilige Dateiendung wahrscheinlichen Tag-Formate werden geprüft.
     *
     * @return eine Scan-Konfiguration mit {@link ScanMode#COMFORT_SCAN}
     */
    public static ScanConfiguration comfortScan() {
        return new ScanConfiguration(ScanMode.COMFORT_SCAN, null);
    }

    /**
     * Erstellt eine Konfiguration für den benutzerdefinierten Scan.
     * <p>
     * Nur die angegebenen Tag-Formate werden geprüft, unabhängig von der Dateiendung.
     *
     * @param formats die zu prüfenden Tag-Formate; mindestens ein Format ist erforderlich
     * @return eine Scan-Konfiguration mit {@link ScanMode#CUSTOM_SCAN} und den angegebenen Formaten
     * @throws IllegalArgumentException wenn {@code formats} {@code null} oder leer ist
     */
    public static ScanConfiguration customScan(TagFormat... formats) {
        if (formats == null || formats.length == 0) {
            throw new IllegalArgumentException("Custom scan requires at least one tag format");
        }
        return new ScanConfiguration(ScanMode.CUSTOM_SCAN, Arrays.asList(formats));
    }

    /**
     * Gibt den konfigurierten Scan-Modus zurück.
     *
     * @return den Scan-Modus
     */
    public ScanMode getMode() {
        return mode;
    }

    /**
     * Gibt die Liste der benutzerdefinierten Tag-Formate zurück.
     * <p>
     * Bei {@link ScanMode#FULL_SCAN} und {@link ScanMode#COMFORT_SCAN} ist die Liste leer.
     *
     * @return eine unveränderliche Liste der benutzerdefinierten Formate,
     *         niemals {@code null}
     */
    public List<TagFormat> getCustomFormats() {
        return customFormats;
    }

    /**
     * Gibt eine textuelle Darstellung dieser Konfiguration zurück.
     *
     * @return eine Zeichenkette mit Modus und benutzerdefinierten Formaten
     */
    @Override
    public String toString() {
        return "ScanConfiguration{" +
                "mode=" + mode +
                ", customFormats=" + customFormats +
                '}';
    }
}