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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class Lyrics3ParsingStrategy implements TagParsingStrategy {
    private static final Logger LOGGER = Logger.getLogger(Lyrics3ParsingStrategy.class.getName());

    private final Map<String, FieldHandler<?>> handlers;

    public Lyrics3ParsingStrategy() {
        this.handlers = new HashMap<>();
        initializeDefaultHandlers();
    }

    private void initializeDefaultHandlers() {
        // Lyrics3v2 Standard-Felder
        handlers.put("LYR", new TextFieldHandler("LYR"));   // Lyrics
        handlers.put("IND", new TextFieldHandler("IND"));   // Indications
        handlers.put("INF", new TextFieldHandler("INF"));   // Additional Information
        handlers.put("AUT", new TextFieldHandler("AUT"));   // Author
        handlers.put("EAL", new TextFieldHandler("EAL"));   // Extended Album name
        handlers.put("EAR", new TextFieldHandler("EAR"));   // Extended Artist name
        handlers.put("ETT", new TextFieldHandler("ETT"));   // Extended Track Title
        handlers.put("IMG", new TextFieldHandler("IMG"));   // Link to an image files

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

        String tagContent = new String(tagData, StandardCharsets.ISO_8859_1);

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
            addField(metadata, "LYRICS", lyricsContent.trim());
        }

        LOGGER.fine("Parsed Lyrics3v1 tag with " + lyricsContent.length() + " characters");
    }

    private void parseLyrics3v2(RandomAccessFile file, Lyrics3Metadata metadata, long offset, long size)
            throws IOException {
        file.seek(offset);
        byte[] tagData = new byte[(int) size];
        int bytesRead = file.read(tagData);

        if (bytesRead != size) {
            throw new IOException("Could not read complete Lyrics3v2 tag");
        }

        String tagContent = new String(tagData, StandardCharsets.ISO_8859_1);

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
            LOGGER.warning("Lyrics3v2 size mismatch: declared=" + declaredSize + ", actual=" + fieldContent.length());
        }

        // Felder parsen
        parseFields(metadata, fieldContent);

        LOGGER.fine("Parsed Lyrics3v2 tag with " + fieldContent.length() + " characters");
    }

    private void parseFields(Lyrics3Metadata metadata, String fieldContent) {
        int position = 0;
        int fieldCount = 0;

        while (position < fieldContent.length() - 5) { // Mindestens 3 Zeichen für Feld-ID + 2 für Größe
            try {
                // Feld-ID (3 Zeichen)
                if (position + 3 > fieldContent.length()) {
                    break;
                }
                String fieldId = fieldContent.substring(position, position + 3);
                position += 3;

                // Feldgröße (2 Zeichen)
                if (position + 2 > fieldContent.length()) {
                    LOGGER.warning("Incomplete field size for field: " + fieldId);
                    break;
                }
                String sizeStr = fieldContent.substring(position, position + 2);
                position += 2;

                int fieldSize;
                try {
                    fieldSize = Integer.parseInt(sizeStr);
                } catch (NumberFormatException e) {
                    LOGGER.warning("Invalid field size for field " + fieldId + ": " + sizeStr);
                    break;
                }

                // Feldinhalt
                if (position + fieldSize > fieldContent.length()) {
                    LOGGER.warning("Field " + fieldId + " extends beyond tag boundary");
                    break;
                }

                String fieldValue = fieldContent.substring(position, position + fieldSize);
                position += fieldSize;

                // Feld hinzufügen, wenn es nicht leer ist
                if (!fieldValue.trim().isEmpty()) {
                    addField(metadata, fieldId, fieldValue.trim());
                    fieldCount++;
                }

                LOGGER.fine("Parsed field: " + fieldId + " = " + fieldValue.substring(0, Math.min(50, fieldValue.length())) +
                        (fieldValue.length() > 50 ? "..." : ""));

            } catch (Exception e) {
                LOGGER.warning("Error parsing field at position " + position + ": " + e.getMessage());
                break;
            }
        }

        LOGGER.fine("Successfully parsed " + fieldCount + " Lyrics3v2 fields");
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
            LOGGER.fine("Created fallback handler for unknown field: " + key);
        }
    }

    public void registerHandler(String key, FieldHandler<?> handler) {
        handlers.put(key, handler);
    }
}