package com.schwanitz.tagging;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Konfigurationsklasse für verschiedene Scan-Modi
 */
public class ScanConfiguration {

    private final ScanMode mode;
    private final List<TagFormat> customFormats;

    private ScanConfiguration(ScanMode mode, List<TagFormat> customFormats) {
        this.mode = mode;
        this.customFormats = customFormats != null ?
                List.copyOf(customFormats) :
                Collections.emptyList();
    }

    /**
     * Erstellt eine Konfiguration für Vollständigen Scan
     */
    public static ScanConfiguration fullScan() {
        return new ScanConfiguration(ScanMode.FULL_SCAN, null);
    }

    /**
     * Erstellt eine Konfiguration für Komfort-Scan
     */
    public static ScanConfiguration comfortScan() {
        return new ScanConfiguration(ScanMode.COMFORT_SCAN, null);
    }

    /**
     * Erstellt eine Konfiguration für benutzerdefinierten Scan
     * @param formats Die zu prüfenden Tag-Formate
     */
    public static ScanConfiguration customScan(TagFormat... formats) {
        if (formats == null || formats.length == 0) {
            throw new IllegalArgumentException("Custom scan requires at least one tag format");
        }
        return new ScanConfiguration(ScanMode.CUSTOM_SCAN, Arrays.asList(formats));
    }

    /**
     * Erstellt eine Konfiguration für benutzerdefinierten Scan
     * @param formats Die zu prüfenden Tag-Formate
     */
    public static ScanConfiguration customScan(List<TagFormat> formats) {
        if (formats == null || formats.isEmpty()) {
            throw new IllegalArgumentException("Custom scan requires at least one tag format");
        }
        return new ScanConfiguration(ScanMode.CUSTOM_SCAN, formats);
    }

    public ScanMode getMode() {
        return mode;
    }

    public List<TagFormat> getCustomFormats() {
        return customFormats;
    }

    @Override
    public String toString() {
        return "ScanConfiguration{" +
                "mode=" + mode +
                ", customFormats=" + customFormats +
                '}';
    }
}