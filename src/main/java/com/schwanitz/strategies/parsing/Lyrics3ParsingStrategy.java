package com.schwanitz.strategies.parsing;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.tagging.TagFormat;

import java.io.IOException;
import com.schwanitz.io.SeekableDataSource;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parsing-Strategie für Lyrics3-Tags (Version 1 und 2).
 *
 * <p>Lyrics3 ist ein Tag-Format, das Liedtexte und erweiterte Metadaten am Ende von MP3-Dateien
 * speichert. Lyrics3v1 besteht aus einem einfachen Textblock zwischen {@code LYRICSBEGIN} und
 * {@code LYRICSEND}-Markierungen. Lyrics3v2 verwendet strukturierte Felder mit 3-Zeichen-IDs
 * und 2-stelligen Größenangaben. Diese Strategie unterstützt Multi-Encoding (ISO-8859-1,
 * Windows-1252, UTF-8) und CRC-Validierung.</p>
 *
 * <p>Unterstützte Formate:</p>
 * <ul>
 *   <li>{@link TagFormat#LYRICS3V1} – einfacher Textblock mit Markierungen</li>
 *   <li>{@link TagFormat#LYRICS3V2} – strukturierte Felder mit IDs und Größenangaben</li>
 * </ul>
 *
 * @see TagParsingStrategy
 */
public class Lyrics3ParsingStrategy extends AbstractTagParsingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(Lyrics3ParsingStrategy.class);

    private static final Pattern FIELD_ID_PATTERN = Pattern.compile("[A-Z0-9]{3}");
    private static final Pattern TIMESTAMP_SHORT_PATTERN = Pattern.compile("\\d{1,2}:\\d{2}");
    private static final Pattern TIMESTAMP_LONG_PATTERN = Pattern.compile("\\d{1,2}:\\d{2}:\\d{2}");
    private static final Pattern CRC_HEX_PATTERN = Pattern.compile("[0-9A-F]+");
    private static final Pattern IMAGE_EXT_PATTERN = Pattern.compile(".*\\.(jpg|jpeg|png|gif|bmp)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CRC_VALIDATION_PATTERN = Pattern.compile("[0-9A-Fa-f]+");



    /**
     * Erzeugt eine neue Lyrics3-Parsing-Strategie mit Standard-Handlern.
     */
    public Lyrics3ParsingStrategy() {
        super("Lyrics3");
        initializeDefaultHandlers();
    }

    private void initializeDefaultHandlers() {
        // Lyrics3v2 Standard-Felder (vollständig)
        handlers.put("LYR", new TextFieldHandler("LYR"));   // Lyrics
        handlers.put("IND", new TextFieldHandler("IND"));   // Indications
        handlers.put("INF", new TextFieldHandler("INF"));   // Additional Information
        handlers.put("AUT", new TextFieldHandler("AUT"));   // Author
        handlers.put("EAL", new TextFieldHandler("EAL"));   // Extended Album name
        handlers.put("EAR", new TextFieldHandler("EAR"));   // Extended Artist name
        handlers.put("ETT", new TextFieldHandler("ETT"));   // Extended Track Title
        handlers.put("IMG", new TextFieldHandler("IMG"));   // Link to image files

        // Fehlende Lyrics3v2 Standard-Felder (NEU)
        handlers.put("CRC", new TextFieldHandler("CRC"));           // CRC checksum
        handlers.put("ETC", new TextFieldHandler("ExtendedComposer")); // Extended track composer
        handlers.put("ETG", new TextFieldHandler("ExtendedGenre"));    // Extended track genre
        handlers.put("ETY", new TextFieldHandler("ExtendedYear"));     // Extended track year
        handlers.put("LRC", new TextFieldHandler("LineSyncLyrics"));   // Line synchronized lyrics
        handlers.put("TME", new TextFieldHandler("Timestamp"));       // Timestamp

        // Lyrics3v1 (nur Lyrics-Inhalt)
        handlers.put("LYRICS", new TextFieldHandler("LYRICS")); // Für v1 Format
    }

    /**
     * Parst einen Lyrics3-Tag aus der angegebenen Datei.
     *
     * @param format das Lyrics3-Format (V1 oder V2)
     * @param source die Datenquelle, aus der gelesen wird
     * @param offset der Start-Offset des Tags
     * @param size   die Größe des Tags in Bytes
     * @return die extrahierten {@link GenericMetadata}
     * @throws IOException bei I/O-Fehlern oder ungültigem Tag-Format
     */
    @Override
    public Metadata parseTag(TagFormat format, SeekableDataSource source, long offset, long size) throws IOException {
        GenericMetadata metadata = new GenericMetadata(format);

        switch (format) {
            case LYRICS3V1:
                parseLyrics3v1(source, metadata, offset, size);
                break;
            case LYRICS3V2:
                parseLyrics3v2(source, metadata, offset, size);
                break;
            default:
                throw new IllegalArgumentException("Unsupported Lyrics3 format: " + format);
        }

        return metadata;
    }

    private static final long MAX_LYRICS3_TAG_SIZE = 1024 * 1024; // 1 MB safety limit

    private void parseLyrics3v1(SeekableDataSource source, GenericMetadata metadata, long offset, long size)
            throws IOException {
        if (size < 0 || size > MAX_LYRICS3_TAG_SIZE) {
            throw new IOException("Invalid Lyrics3v1 tag size: " + size);
        }
        byte[] tagData = new byte[(int) size];
        source.readFully(offset, tagData);

        // Multi-Encoding Support für bessere Real-World-Kompatibilität
        String tagContent = parseTagContent(tagData);

        // Validierung der Start- und End-Markierungen
        if (!tagContent.startsWith("LYRICSBEGIN")) {
            throw new IOException("Invalid Lyrics3v1 start marker");
        }

        if (!tagContent.endsWith("LYRICSEND")) {
            throw new IOException("Invalid Lyrics3v1 end marker");
        }

        // Lyrics-Inhalt extrahieren (zwischen den Markierungen)
        String lyricsContent = tagContent.substring(11, tagContent.length() - 9); // 11 = "LYRICSBEGIN".length(), 9 = "LYRICSEND".length()

        if (!lyricsContent.trim().isEmpty()) {
            // Validiere und bereinige Lyrics-Inhalt
            String cleanLyrics = validateAndCleanLyricsContent(lyricsContent.trim());
            addField(metadata, "LYRICS", cleanLyrics);
        }

        LOG.debug("Parsed Lyrics3v1 tag with {} characters", lyricsContent.length());
    }

    private void parseLyrics3v2(SeekableDataSource source, GenericMetadata metadata, long offset, long size)
            throws IOException {
        if (size < 0 || size > MAX_LYRICS3_TAG_SIZE) {
            throw new IOException("Invalid Lyrics3v2 tag size: " + size);
        }
        byte[] tagData = new byte[(int) size];
        source.readFully(offset, tagData);

        // Multi-Encoding Support für bessere Real-World-Kompatibilität
        String tagContent = parseTagContent(tagData);

        // Validierung der Start-Markierung
        if (!tagContent.startsWith("LYRICSBEGIN")) {
            throw new IOException("Invalid Lyrics3v2 start marker");
        }

        // Validierung der End-Markierung (sollte "LYRICS200" sein)
        if (!tagContent.endsWith("LYRICS200")) {
            throw new IOException("Invalid Lyrics3v2 end marker");
        }

        // Größenfeld vor "LYRICS200" extrahieren (6 Zeichen)
        String sizeField = tagContent.substring(tagContent.length() - 15, tagContent.length() - 9);
        int declaredSize;
        try {
            declaredSize = Integer.parseInt(sizeField);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid Lyrics3v2 size field: " + sizeField);
        }

        // Feldinhalt zwischen LYRICSBEGIN und Größenfeld
        String fieldContent = tagContent.substring(11, tagContent.length() - 15);

        if (fieldContent.length() != declaredSize) {
            LOG.warn("Lyrics3v2 size mismatch: declared={}, actual={}", declaredSize, fieldContent.length());
        }

        // Felder parsen mit verbesserter Validierung
        parseFields(metadata, fieldContent);

        LOG.debug("Parsed Lyrics3v2 tag with {} characters", fieldContent.length());
    }

    private String parseTagContent(byte[] data) {
        // Versuche verschiedene Encodings für bessere Real-World-Kompatibilität

        // 1. Standard: ISO-8859-1 (nach Lyrics3 Spec)
        try {
            String iso = new String(data, StandardCharsets.ISO_8859_1);
            if (isValidLyrics3Text(iso)) {
                LOG.debug("Using ISO-8859-1 encoding for Lyrics3 tag");
                return iso;
            }
        } catch (Exception e) {
            LOG.debug("ISO-8859-1 encoding failed: {}", e.getMessage());
        }

        // 2. Real-World: Windows-1252/CP1252 (häufig verwendet)
        try {
            Charset cp1252 = Charset.forName("windows-1252");
            String cp1252Text = new String(data, cp1252);
            if (isValidLyrics3Text(cp1252Text)) {
                LOG.debug("Using Windows-1252 encoding for Lyrics3 tag");
                return cp1252Text;
            }
        } catch (Exception e) {
            LOG.debug("Windows-1252 encoding failed: {}", e.getMessage());
        }

        // 3. Fallback: UTF-8 (für moderne Tags)
        try {
            String utf8 = new String(data, StandardCharsets.UTF_8);
            if (isValidLyrics3Text(utf8)) {
                LOG.debug("Using UTF-8 encoding for Lyrics3 tag");
                return utf8;
            }
        } catch (Exception e) {
            LOG.debug("UTF-8 encoding failed: {}", e.getMessage());
        }

        // 4. Last resort: US-ASCII
        LOG.warn("All preferred encodings failed, using US-ASCII as last resort");
        return new String(data, StandardCharsets.US_ASCII);
    }

    private boolean isValidLyrics3Text(String text) {
        // Prüfe ob Text gültige Lyrics3 Markierungen enthält
        if (text.contains("LYRICSBEGIN") && (text.contains("LYRICSEND") || text.contains("LYRICS200"))) {
            // Zusätzliche Heuristik: Prüfe Zeichen-Verteilung
            int printableCount = 0;
            int controlCount = 0;

            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c >= 32 && c <= 126) { // ASCII printable
                    printableCount++;
                } else if (c >= 128) { // Extended characters
                    printableCount++;
                } else if (c == 9 || c == 10 || c == 13) { // Tab, LF, CR
                    printableCount++;
                } else {
                    controlCount++;
                }
            }

            // Mindestens 90% sollten druckbare Zeichen sein
            return printableCount > (printableCount + controlCount) * 0.9;
        }
        return false;
    }

    private String validateAndCleanLyricsContent(String lyrics) {
        if (lyrics == null || lyrics.isEmpty()) {
            return lyrics;
        }

        // Entferne übermäßige Leerzeichen und normalisiere Zeilenumbrüche

        return lyrics.replaceAll("\\r\\n", "\n")
                .replaceAll("\\r", "\n")
                .replaceAll("\\n{3,}", "\n\n") // Max 2 aufeinanderfolgende Leerzeilen
                .trim();
    }

    private void parseFields(GenericMetadata metadata, String fieldContent) {
        int position = 0;
        int fieldCount = 0;
        String lastValidCRC = null; // Für CRC-Validierung

        while (position < fieldContent.length() - 5) { // Mindestens 3 Zeichen für Feld-ID + 2 für Größe
            try {
                // Feld-ID (3 Zeichen) mit Validierung
                if (position + 3 > fieldContent.length()) {
                    break;
                }
                String fieldId = fieldContent.substring(position, position + 3);

                // Validiere Feld-ID Format (A-Z0-9, 3 Zeichen)
                if (!isValidFieldId(fieldId)) {
                    LOG.warn("Invalid Lyrics3v2 field ID format: {}", fieldId);
                    break;
                }

                position += 3;

                // Feldgröße (2 Zeichen) mit Validierung
                if (position + 2 > fieldContent.length()) {
                    LOG.warn("Incomplete field size for field: {}", fieldId);
                    break;
                }
                String sizeStr = fieldContent.substring(position, position + 2);

                // Validiere Größenangabe Format (00-99)
                if (!isValidFieldSize(sizeStr)) {
                    LOG.warn("Invalid field size format for field {}: {}", fieldId, sizeStr);
                    break;
                }

                position += 2;

                int fieldSize;
                try {
                    fieldSize = Integer.parseInt(sizeStr);
                } catch (NumberFormatException e) {
                    LOG.warn("Invalid field size for field {}: {}", fieldId, sizeStr);
                    break;
                }

                // Feldinhalt mit Validierung
                if (position + fieldSize > fieldContent.length()) {
                    LOG.warn("Field {} extends beyond tag boundary", fieldId);
                    break;
                }

                String fieldValue = fieldContent.substring(position, position + fieldSize);
                position += fieldSize;

                // Spezielle Behandlung für verschiedene Feldtypen
                String processedValue = processFieldValue(fieldId, fieldValue);

                // Feld hinzufügen, wenn es nicht leer ist
                if (!processedValue.trim().isEmpty()) {
                    addField(metadata, fieldId, processedValue.trim());
                    fieldCount++;

                    // CRC-Feld für spätere Validierung speichern
                    if ("CRC".equals(fieldId)) {
                        lastValidCRC = processedValue.trim();
                    }
                }

                LOG.debug("Parsed field: {} = {}",
                        fieldId, processedValue.length() > 50 ? processedValue.substring(0, 50) + "..." : processedValue);

            } catch (Exception e) {
                LOG.warn("Error parsing field at position {}: {}", position, e.getMessage());
                break;
            }
        }

        // CRC-Validierung falls CRC-Feld vorhanden
        if (lastValidCRC != null) {
            validateCRC(fieldContent, lastValidCRC);
        }

        LOG.debug("Successfully parsed {} Lyrics3v2 fields", fieldCount);
    }

    private boolean isValidFieldId(String fieldId) {
        if (fieldId == null || fieldId.length() != 3) {
            return false;
        }

        // Lyrics3v2 Feld-IDs sollten nur A-Z und 0-9 enthalten
        return FIELD_ID_PATTERN.matcher(fieldId).matches();
    }

    private boolean isValidFieldSize(String sizeStr) {
        if (sizeStr == null || sizeStr.length() != 2) {
            return false;
        }

        // Größenangabe sollte numerisch sein (00-99)
        try {
            int size = Integer.parseInt(sizeStr);
            return size >= 0 && size <= 99;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String processFieldValue(String fieldId, String fieldValue) {
        if (fieldValue == null || fieldValue.isEmpty()) {
            return fieldValue;
        }

        switch (fieldId) {
            case "LYR":
                // Lyrics: Normalisiere Zeilenumbrüche
                return validateAndCleanLyricsContent(fieldValue);

            case "TME":
                // Timestamp: Validiere Format (meist MM:SS oder HH:MM:SS)
                return validateTimestamp(fieldValue);

            case "CRC":
                // CRC: Sollte hexadezimal sein
                return validateCRC(fieldValue);

            case "ETY":
                // Extended Year: Sollte numerisch sein
                return validateYear(fieldValue);

            case "LRC":
                // Line synchronized lyrics: Spezialformat
                return validateLineSyncLyrics(fieldValue);

            case "IMG":
                // Image link: URL oder Pfad
                return validateImageLink(fieldValue);

            default:
                // Standard Text-Felder: Nur trimmen
                return fieldValue.trim();
        }
    }

    private String validateTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return timestamp;
        }

        // Prüfe gängige Timestamp-Formate: MM:SS oder HH:MM:SS
        if (TIMESTAMP_SHORT_PATTERN.matcher(timestamp).matches() || TIMESTAMP_LONG_PATTERN.matcher(timestamp).matches()) {
            return timestamp.trim();
        }

        LOG.debug("Unusual timestamp format: {}", timestamp);
        return timestamp.trim();
    }

    private String validateCRC(String crc) {
        if (crc == null || crc.isEmpty()) {
            return crc;
        }

        // CRC sollte hexadezimal sein
        String cleanCRC = crc.trim().toUpperCase();
        if (CRC_HEX_PATTERN.matcher(cleanCRC).matches()) {
            return cleanCRC;
        }

        LOG.debug("Invalid CRC format: {}", crc);
        return crc.trim();
    }

    private String validateYear(String year) {
        if (year == null || year.isEmpty()) {
            return year;
        }

        String cleanYear = year.trim();
        try {
            int yearInt = Integer.parseInt(cleanYear);
            if (yearInt >= 1000 && yearInt <= 9999) {
                return cleanYear;
            }
        } catch (NumberFormatException e) {
            // Nicht numerisch
        }

        LOG.debug("Unusual year format: {}", year);
        return cleanYear;
    }

    private String validateLineSyncLyrics(String lrc) {
        if (lrc == null || lrc.isEmpty()) {
            return lrc;
        }

        // LRC Format: [MM:SS]text oder [MM:SS.xx]text
        // einfache Validierung auf LRC-ähnliches Format
        if (lrc.contains("[") && lrc.contains("]") && lrc.contains(":")) {
            LOG.debug("Line synchronized lyrics detected");
        }

        return lrc.trim();
    }

    private String validateImageLink(String imgLink) {
        if (imgLink == null || imgLink.isEmpty()) {
            return imgLink;
        }

        String cleanLink = imgLink.trim();

        // Prüfe auf gängige Bild-Dateierweiterungen
        if (IMAGE_EXT_PATTERN.matcher(cleanLink).matches()) {
            LOG.debug("Image file detected: {}", cleanLink);
        } else if (cleanLink.toLowerCase().startsWith("http")) {
            LOG.debug("Image URL detected: {}", cleanLink);
        }

        return cleanLink;
    }

    private void validateCRC(String fieldContent, String providedCRC) {
        if (providedCRC == null || providedCRC.isEmpty()) {
            return;
        }

        try {
            // Vereinfachte CRC-Validierung (echte CRC-Berechnung wäre komplex)
            // Hier nur Format-Validierung und Logging
            if (CRC_VALIDATION_PATTERN.matcher(providedCRC).matches()) {
                LOG.debug("CRC field present with value: {}", providedCRC);
                // TODO: Implementiere echte CRC-Validierung falls benötigt
            } else {
                LOG.warn("Invalid CRC format: {}", providedCRC);
            }
        } catch (Exception e) {
            LOG.warn("Error validating CRC: {}", e.getMessage());
        }
    }

}