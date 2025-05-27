package com.schwanitz.strategies.parsing;

import com.schwanitz.interfaces.FieldHandler;
import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.Lyrics3Metadata;
import com.schwanitz.others.MetadataField;
import com.schwanitz.others.TextFieldHandler;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.tagging.TagFormat;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Lyrics3ParsingStrategy implements TagParsingStrategy {

    private static final Logger Log = LoggerFactory.getLogger(Lyrics3ParsingStrategy.class);

    private final Map<String, FieldHandler<?>> handlers;

    public Lyrics3ParsingStrategy() {
        this.handlers = new HashMap<>();
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

    @Override
    public boolean canHandle(TagFormat format) {
        return format == TagFormat.LYRICS3V1 || format == TagFormat.LYRICS3V2;
    }

    @Override
    public Metadata parseTag(TagFormat format, RandomAccessFile file, long offset, long size) throws IOException {
        Lyrics3Metadata metadata = new Lyrics3Metadata(format);

        switch (format) {
            case LYRICS3V1:
                parseLyrics3v1(file, metadata, offset, size);
                break;
            case LYRICS3V2:
                parseLyrics3v2(file, metadata, offset, size);
                break;
            default:
                throw new IllegalArgumentException("Unsupported Lyrics3 format: " + format);
        }

        return metadata;
    }

    private void parseLyrics3v1(RandomAccessFile file, Lyrics3Metadata metadata, long offset, long size)
            throws IOException {
        file.seek(offset);
        byte[] tagData = new byte[(int) size];
        int bytesRead = file.read(tagData);

        if (bytesRead != size) {
            throw new IOException("Could not read complete Lyrics3v1 tag");
        }

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

        Log.debug("Parsed Lyrics3v1 tag with " + lyricsContent.length() + " characters");
    }

    private void parseLyrics3v2(RandomAccessFile file, Lyrics3Metadata metadata, long offset, long size)
            throws IOException {
        file.seek(offset);
        byte[] tagData = new byte[(int) size];
        int bytesRead = file.read(tagData);

        if (bytesRead != size) {
            throw new IOException("Could not read complete Lyrics3v2 tag");
        }

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
            Log.warn("Lyrics3v2 size mismatch: declared=" + declaredSize + ", actual=" + fieldContent.length());
        }

        // Felder parsen mit verbesserter Validierung
        parseFields(metadata, fieldContent);

        Log.debug("Parsed Lyrics3v2 tag with " + fieldContent.length() + " characters");
    }

    private String parseTagContent(byte[] data) {
        // Versuche verschiedene Encodings für bessere Real-World-Kompatibilität

        // 1. Standard: ISO-8859-1 (nach Lyrics3 Spec)
        try {
            String iso = new String(data, StandardCharsets.ISO_8859_1);
            if (isValidLyrics3Text(iso)) {
                Log.debug("Using ISO-8859-1 encoding for Lyrics3 tag");
                return iso;
            }
        } catch (Exception e) {
            Log.debug("ISO-8859-1 encoding failed: " + e.getMessage());
        }

        // 2. Real-World: Windows-1252/CP1252 (häufig verwendet)
        try {
            Charset cp1252 = Charset.forName("windows-1252");
            String cp1252Text = new String(data, cp1252);
            if (isValidLyrics3Text(cp1252Text)) {
                Log.debug("Using Windows-1252 encoding for Lyrics3 tag");
                return cp1252Text;
            }
        } catch (Exception e) {
            Log.debug("Windows-1252 encoding failed: " + e.getMessage());
        }

        // 3. Fallback: UTF-8 (für moderne Tags)
        try {
            String utf8 = new String(data, StandardCharsets.UTF_8);
            if (isValidLyrics3Text(utf8)) {
                Log.debug("Using UTF-8 encoding for Lyrics3 tag");
                return utf8;
            }
        } catch (Exception e) {
            Log.debug("UTF-8 encoding failed: " + e.getMessage());
        }

        // 4. Last resort: US-ASCII
        Log.warn("All preferred encodings failed, using US-ASCII as last resort");
        return new String(data, StandardCharsets.US_ASCII);
    }

    private boolean isValidLyrics3Text(String text) {
        // Prüfe ob Text gültige Lyrics3 Markierungen enthält
        if (text.contains("LYRICSBEGIN") && (text.contains("LYRICSEND") || text.contains("LYRICS200"))) {
            // Zusätzliche Heuristik: Prüfe Zeichen-Verteilung
            int printableCount = 0;
            int controlCount = 0;

            for (char c : text.toCharArray()) {
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
        String cleaned = lyrics.replaceAll("\\r\\n", "\n")
                .replaceAll("\\r", "\n")
                .replaceAll("\\n{3,}", "\n\n") // Max 2 aufeinanderfolgende Leerzeilen
                .trim();

        return cleaned;
    }

    private void parseFields(Lyrics3Metadata metadata, String fieldContent) {
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
                    Log.warn("Invalid Lyrics3v2 field ID format: " + fieldId);
                    break;
                }

                position += 3;

                // Feldgröße (2 Zeichen) mit Validierung
                if (position + 2 > fieldContent.length()) {
                    Log.warn("Incomplete field size for field: " + fieldId);
                    break;
                }
                String sizeStr = fieldContent.substring(position, position + 2);

                // Validiere Größenangabe Format (00-99)
                if (!isValidFieldSize(sizeStr)) {
                    Log.warn("Invalid field size format for field " + fieldId + ": " + sizeStr);
                    break;
                }

                position += 2;

                int fieldSize;
                try {
                    fieldSize = Integer.parseInt(sizeStr);
                } catch (NumberFormatException e) {
                    Log.warn("Invalid field size for field " + fieldId + ": " + sizeStr);
                    break;
                }

                // Feldinhalt mit Validierung
                if (position + fieldSize > fieldContent.length()) {
                    Log.warn("Field " + fieldId + " extends beyond tag boundary");
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

                Log.debug("Parsed field: " + fieldId + " = " +
                        (processedValue.length() > 50 ? processedValue.substring(0, 50) + "..." : processedValue));

            } catch (Exception e) {
                Log.warn("Error parsing field at position " + position + ": " + e.getMessage());
                break;
            }
        }

        // CRC-Validierung falls CRC-Feld vorhanden
        if (lastValidCRC != null) {
            validateCRC(fieldContent, lastValidCRC);
        }

        Log.debug("Successfully parsed " + fieldCount + " Lyrics3v2 fields");
    }

    private boolean isValidFieldId(String fieldId) {
        if (fieldId == null || fieldId.length() != 3) {
            return false;
        }

        // Lyrics3v2 Feld-IDs sollten nur A-Z und 0-9 enthalten
        return fieldId.matches("[A-Z0-9]{3}");
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
        if (timestamp.matches("\\d{1,2}:\\d{2}") || timestamp.matches("\\d{1,2}:\\d{2}:\\d{2}")) {
            return timestamp.trim();
        }

        Log.debug("Unusual timestamp format: " + timestamp);
        return timestamp.trim();
    }

    private String validateCRC(String crc) {
        if (crc == null || crc.isEmpty()) {
            return crc;
        }

        // CRC sollte hexadezimal sein
        String cleanCRC = crc.trim().toUpperCase();
        if (cleanCRC.matches("[0-9A-F]+")) {
            return cleanCRC;
        }

        Log.debug("Invalid CRC format: " + crc);
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

        Log.debug("Unusual year format: " + year);
        return cleanYear;
    }

    private String validateLineSyncLyrics(String lrc) {
        if (lrc == null || lrc.isEmpty()) {
            return lrc;
        }

        // LRC Format: [MM:SS]text oder [MM:SS.xx]text
        // einfache Validierung auf LRC-ähnliches Format
        if (lrc.contains("[") && lrc.contains("]") && lrc.contains(":")) {
            Log.debug("Line synchronized lyrics detected");
        }

        return lrc.trim();
    }

    private String validateImageLink(String imgLink) {
        if (imgLink == null || imgLink.isEmpty()) {
            return imgLink;
        }

        String cleanLink = imgLink.trim();

        // Prüfe auf gängige Bild-Dateierweiterungen
        if (cleanLink.toLowerCase().matches(".*\\.(jpg|jpeg|png|gif|bmp)$")) {
            Log.debug("Image file detected: " + cleanLink);
        } else if (cleanLink.toLowerCase().startsWith("http")) {
            Log.debug("Image URL detected: " + cleanLink);
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
            if (providedCRC.matches("[0-9A-Fa-f]+")) {
                Log.debug("CRC field present with value: " + providedCRC);
                // TODO: Implementiere echte CRC-Validierung falls benötigt
            } else {
                Log.warn("Invalid CRC format: " + providedCRC);
            }
        } catch (Exception e) {
            Log.warn("Error validating CRC: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void addField(Lyrics3Metadata metadata, String key, String value) {
        FieldHandler<?> handler = handlers.get(key);
        if (handler != null) {
            metadata.addField(new MetadataField<>(key, value, (FieldHandler<String>) handler));
        } else {
            // Fallback: TextFieldHandler für unbekannte Felder erstellen
            TextFieldHandler textHandler = new TextFieldHandler(key);
            metadata.addField(new MetadataField<>(key, value, textHandler));
            Log.debug("Created fallback handler for unknown field: " + key);
        }
    }

    public void registerHandler(String key, FieldHandler<?> handler) {
        handlers.put(key, handler);
    }
}