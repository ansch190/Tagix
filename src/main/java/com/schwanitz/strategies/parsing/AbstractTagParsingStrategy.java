package com.schwanitz.strategies.parsing;

import com.schwanitz.interfaces.FieldHandler;
import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Abstrakte Basisklasse für alle Tag-Parsing-Strategien.
 * <p>
 * Kapselt die gemeinsame Logik, die in allen 14 Parsing-Strategien dupliziert war:
 * <ul>
 *   <li>Handler-Map-Verwaltung</li>
 *   <li>{@code addField()} mit Fallback-Handler und optionaler Format-Log-Ausgabe</li>
 *   <li>{@code registerHandler()}-API</li>
 *   <li>Statische Utility-Methoden für häufige Validierungen</li>
 * </ul>
 *
 * @see TagParsingStrategy
 */
public abstract class AbstractTagParsingStrategy implements TagParsingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractTagParsingStrategy.class);

    protected final Map<String, FieldHandler<?>> handlers = new HashMap<>();
    private final String strategyName;

    /**
     * Erzeugt eine neue Parsing-Strategie mit dem angegebenen Formatnamen für Logging.
     *
     * @param strategyName der Formatname (z. B. "ID3", "APE", "MP4") für Log-Ausgaben
     */
    protected AbstractTagParsingStrategy(String strategyName) {
        this.strategyName = strategyName;
    }

    /**
     * Fügt ein Feld den Metadaten hinzu. Verwendet einen registrierten {@link FieldHandler}
     * oder erzeugt einen Fallback-{@link TextFieldHandler}.
     *
     * @param metadata die Ziel-Metadaten
     * @param key      der Feld-Schlüssel
     * @param value    der Feld-Wert
     */
    @SuppressWarnings("unchecked")
    protected void addField(Metadata metadata, String key, String value) {
        addField(metadata, key, value, false, false, false);
    }

    /**
     * Fügt ein Feld den Metadaten hinzu mit optionalen Verhaltensweisen.
     *
     * @param metadata       die Ziel-Metadaten
     * @param key            der Feld-Schlüssel
     * @param value          der Feld-Wert
     * @param skipIfEmpty    {@code true}, um leere/null-Werte zu überspringen
     * @param normalizeKey   {@code true}, um den Schlüssel in Großbuchstaben zu normalisieren
     * @param removeExisting {@code true}, um existierende Felder mit gleichem Schlüssel zu entfernen
     */
    @SuppressWarnings("unchecked")
    protected void addField(Metadata metadata, String key, String value,
                            boolean skipIfEmpty, boolean normalizeKey, boolean removeExisting) {
        if (skipIfEmpty && (value == null || value.isEmpty())) {
            return;
        }
        String actualKey = normalizeKey ? key.toUpperCase() : key;
        if (removeExisting && metadata instanceof GenericMetadata gm) {
            gm.removeField(actualKey);
        }
        FieldHandler<?> handler = handlers.get(actualKey);
        if (handler != null) {
            metadata.addField(new MetadataField<>(actualKey, value, (FieldHandler<String>) handler));
        } else {
            metadata.addField(new MetadataField<>(actualKey, value, new TextFieldHandler(actualKey)));
            if (LOG.isDebugEnabled()) {
                LOG.debug("Created fallback handler for unknown {} field: {}", strategyName, actualKey);
            }
        }
    }

    /**
     * Registriert einen benutzerdefinierten {@link FieldHandler} für einen bestimmten Schlüssel.
     *
     * @param key     der Feld-Schlüssel
     * @param handler der zu registrierende Handler
     */
    public void registerHandler(String key, FieldHandler<?> handler) {
        handlers.put(key, handler);
    }

    // ================================
    // Static utilities
    // ================================

    /**
     * Kürzt einen String auf die angegebene Maximallänge für Debug-Ausgaben.
     *
     * @param value     der zu kürzende String
     * @param maxLength die Maximallänge (inkl. "…")
     * @return der gekürzte String oder der Original-String, wenn er kürzer ist
     */
    public static String truncateForDisplay(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "…";
    }

    /**
     * Prüft, ob ein String ausschließlich druckbare ASCII-Zeichen enthält.
     *
     * @param text           der zu prüfende String
     * @param allowExtended  {@code true}, um Zeichen >= 128 als gültig zu werten
     * @param allowTabsCrLf  {@code true}, um Tab (\t), LF (\n) und CR (\r) als gültig zu werten
     * @return {@code true}, wenn der String gültig ist
     */
    public static boolean isValidAsciiPrintable(String text, boolean allowExtended, boolean allowTabsCrLf) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 32 && c <= 126) {
                continue;
            }
            if (allowExtended && c >= 128) {
                continue;
            }
            if (allowTabsCrLf && (c == 9 || c == 10 || c == 13)) {
                continue;
            }
            return false;
        }
        return true;
    }
}
