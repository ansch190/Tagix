package com.schwanitz.strategies.parsing;

import com.schwanitz.interfaces.FieldHandler;
import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.io.BinaryDataReader;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.tagging.TagFormat;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parsing-Strategie für AIFF-Metadaten-Chunks.
 *
 * <p>AIFF-Dateien (Audio Interchange File Format) speichern Metadaten in benannten Chunks
 * wie {@code NAME} (Titel), {@code AUTH} (Künstler), {@code (c) } (Copyright), {@code ANNO}
 * (Annotation) und {@code COMT} (strukturierte Kommentare). Diese Strategie liest die Chunk-Struktur
 * und wandelt Text-Chunks in Metadatenfelder um. Der {@code ID3}-Chunk wird ignoriert, da er
 * von der separaten {@link ID3ParsingStrategy} behandelt wird.</p>
 *
 * <p>Unterstütztes Format:</p>
 * <ul>
 *   <li>{@link TagFormat#AIFF_METADATA}</li>
 * </ul>
 *
 * @see TagParsingStrategy
 * @see ID3ParsingStrategy
 */
public class AIFFMetadataParsingStrategy implements TagParsingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(AIFFMetadataParsingStrategy.class);

    // AIFF Metadata Chunk Types
    private static final Map<String, String> AIFF_CHUNKS = new HashMap<>();

    static {
        // Standard AIFF Metadata Chunks
        AIFF_CHUNKS.put("NAME", "Title");        // Name/Title
        AIFF_CHUNKS.put("AUTH", "Artist");       // Author/Artist
        AIFF_CHUNKS.put("(c) ", "Copyright");    // Copyright
        AIFF_CHUNKS.put("ANNO", "Annotation");   // Annotation/Comment
        AIFF_CHUNKS.put("COMT", "Comment");      // Comment chunk (structured)
        AIFF_CHUNKS.put("MIDI", "MIDI");         // MIDI data
        AIFF_CHUNKS.put("AESD", "AudioData");    // Audio recording data
        AIFF_CHUNKS.put("APPL", "Application");  // Application specific
        AIFF_CHUNKS.put("ID3 ", "ID3");          // ID3 Tag (handled separately)
    }

    private final Map<String, FieldHandler<?>> handlers;

    /**
     * Erzeugt eine neue AIFF-Parsing-Strategie mit Standard-Handlern.
     */
    public AIFFMetadataParsingStrategy() {
        this.handlers = new HashMap<>();
        initializeDefaultHandlers();
    }

    private void initializeDefaultHandlers() {
        // Handler für alle bekannten AIFF Felder erstellen
        for (String fieldName : AIFF_CHUNKS.values()) {
            if (!"ID3".equals(fieldName)) { // ID3 wird separat behandelt
                handlers.put(fieldName, new TextFieldHandler(fieldName));
            }
        }
    }

    /**
     * Prüft, ob diese Strategie das angegebene Tag-Format verarbeiten kann.
     *
     * @param format das zu prüfende Tag-Format
     * @return {@code true} für {@link TagFormat#AIFF_METADATA}
     */
    @Override
    public boolean canHandle(TagFormat format) {
        return format == TagFormat.AIFF_METADATA;
    }

    /**
     * Parst AIFF-Metadaten aus der angegebenen Datei.
     *
     * @param format das AIFF_METADATA-Format
     * @param file   die Datei, aus der gelesen wird
     * @param offset der Start-Offset des AIFF-Chunks
     * @param size   die Größe des Chunks in Bytes
     * @return die extrahierten {@link GenericMetadata}
     * @throws IOException bei I/O-Fehlern oder ungültigem Chunk-Format
     */
    @Override
    public Metadata parseTag(TagFormat format, RandomAccessFile file, long offset, long size) throws IOException {
        GenericMetadata metadata = new GenericMetadata(TagFormat.AIFF_METADATA);
        parseAIFFMetadataChunk(file, metadata, offset, size);
        return metadata;
    }

    private void parseAIFFMetadataChunk(RandomAccessFile file, GenericMetadata metadata, long offset, long size)
            throws IOException {
        file.seek(offset);

        // AIFF Chunk Header lesen (8 bytes)
        byte[] chunkHeader = new byte[8];
        file.read(chunkHeader);

        String chunkType = new String(chunkHeader, 0, 4, StandardCharsets.US_ASCII);
        int chunkSize = BinaryDataReader.readBigEndianInt32(chunkHeader, 4);

        LOG.debug("Parsing AIFF chunk: {} with size: {}", chunkType, chunkSize);

        if (chunkSize < 0 || chunkSize > size - 8) {
            throw new IOException("Invalid AIFF chunk size: " + chunkSize);
        }

        // Chunk-spezifische Verarbeitung
        switch (chunkType) {
            case "NAME":
            case "AUTH":
            case "(c) ":
            case "ANNO":
                parseTextChunk(file, metadata, chunkType, chunkSize);
                break;

            case "COMT":
                parseCommentChunk(file, metadata, chunkSize);
                break;

            case "APPL":
                parseApplicationChunk(file, metadata, chunkSize);
                break;

            case "ID3 ":
                // ID3 Tags werden von separater ID3ParsingStrategy behandelt
                LOG.debug("Skipping ID3 chunk - handled by ID3ParsingStrategy");
                break;

            default:
                // Unbekannter Chunk - versuche als Text zu parsen
                if (chunkSize > 0 && chunkSize < 8192) { // Reasonable size limit
                    parseTextChunk(file, metadata, chunkType, chunkSize);
                } else {
                    LOG.debug("Skipping unknown AIFF chunk: {}", chunkType);
                }
                break;
        }

        LOG.debug("Successfully parsed AIFF metadata chunk: {}", chunkType);
    }

    private void parseTextChunk(RandomAccessFile file, GenericMetadata metadata, String chunkType, int chunkSize)
            throws IOException {
        if (chunkSize <= 0) {
            return;
        }

        byte[] textData = new byte[chunkSize];
        file.read(textData);

        // Text parsen (normalerweise US-ASCII oder UTF-8)
        String text = parseTextData(textData);

        if (!text.isEmpty()) {
            String fieldName = AIFF_CHUNKS.getOrDefault(chunkType, chunkType);
            addField(metadata, fieldName, text);

            if (LOG.isDebugEnabled()) {
                String displayText = text.length() > 50 ? text.substring(0, 50) + "..." : text;
                LOG.debug("Parsed AIFF text field: {} ({}) = {}", chunkType, fieldName, displayText);
            }
        }
    }

    private void parseCommentChunk(RandomAccessFile file, GenericMetadata metadata, int chunkSize)
            throws IOException {
        if (chunkSize < 2) {
            return;
        }

        // Comment Chunk hat strukturiertes Format
        int numComments = BinaryDataReader.readBigEndianInt16(file);
        int bytesRead = 2;

        for (int i = 0; i < numComments && bytesRead < chunkSize; i++) {
            if (bytesRead + 8 > chunkSize) {
                break;
            }

            // Comment Entry: timeStamp (4) + marker (2) + count (2) + text (count)
            int timeStamp = BinaryDataReader.readBigEndianInt32(file);
            int marker = BinaryDataReader.readBigEndianInt16(file);
            int count = BinaryDataReader.readBigEndianInt16(file);
            bytesRead += 8;

            if (count > 0 && bytesRead + count <= chunkSize) {
                byte[] commentText = new byte[count];
                file.read(commentText);
                bytesRead += count;

                String text = parseTextData(commentText);
                if (!text.isEmpty()) {
                    String commentField = "Comment" + (i > 0 ? "_" + i : "");
                    addField(metadata, commentField, text + " [marker:" + marker + "]");
                }
            }

            // Padding für gerade Byte-Grenze
            if (count % 2 != 0 && bytesRead < chunkSize) {
                file.skipBytes(1);
                bytesRead++;
            }
        }
    }

    private void parseApplicationChunk(RandomAccessFile file, GenericMetadata metadata, int chunkSize)
            throws IOException {
        if (chunkSize < 4) {
            return;
        }

        // Application Signature (4 bytes)
        byte[] signature = new byte[4];
        file.read(signature);
        String appSignature = new String(signature, StandardCharsets.US_ASCII);

        // Application Data (rest of chunk)
        int dataSize = chunkSize - 4;
        if (dataSize > 0) {
            // Für bekannte Applications spezielle Behandlung
            if ("pdos".equals(appSignature) || "stoc".equals(appSignature)) {
                // ProTools oder andere DAW-spezifische Daten
                byte[] appData = new byte[Math.min(dataSize, 256)]; // Limit for safety
                file.read(appData);

                String appDataText = parseTextData(appData);
                if (!appDataText.isEmpty()) {
                    addField(metadata, "Application", appSignature + ": " + appDataText);
                }
            } else {
                // Unbekannte Application
                addField(metadata, "Application", appSignature + " [" + dataSize + " bytes]");
                file.skipBytes(dataSize);
            }
        }
    }

    private String parseTextData(byte[] data) {
        if (data.length == 0) {
            return "";
        }

        // Entferne trailing nulls
        int length = data.length;
        while (length > 0 && data[length - 1] == 0) {
            length--;
        }

        if (length == 0) {
            return "";
        }

        // Versuche verschiedene Encodings
        try {
            // Versuche UTF-8
            String utf8 = new String(data, 0, length, StandardCharsets.UTF_8);
            if (isValidText(utf8)) {
                return utf8.trim();
            }
        } catch (Exception e) {
            // Fallback
        }

        // Fallback zu US-ASCII (AIFF Standard)
        try {
            return new String(data, 0, length, StandardCharsets.US_ASCII).trim();
        } catch (Exception e) {
            // Als letzter Ausweg: ISO-8859-1
            return new String(data, 0, length, StandardCharsets.ISO_8859_1).trim();
        }
    }

    private boolean isValidText(String text) {
        // Einfache Heuristik für gültigen Text
        for (char c : text.toCharArray()) {
            if (c < 32 && c != 9 && c != 10 && c != 13) { // Kontrollzeichen außer Tab, LF, CR
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private void addField(GenericMetadata metadata, String key, String value) {
        FieldHandler<?> handler = handlers.get(key);
        if (handler != null) {
            metadata.addField(new MetadataField<>(key, value, (FieldHandler<String>) handler));
        } else {
            TextFieldHandler textHandler = new TextFieldHandler(key);
            metadata.addField(new MetadataField<>(key, value, textHandler));
            LOG.debug("Created fallback handler for unknown AIFF field: {}", key);
        }
    }

    /**
     * Registriert einen benutzerdefinierten {@link FieldHandler} für ein bestimmtes AIFF-Feld.
     *
     * @param key     der Feldname, für den der Handler registriert werden soll
     * @param handler der zu registrierende Handler
     */
    public void registerHandler(String key, FieldHandler<?> handler) {
        handlers.put(key, handler);
    }

}