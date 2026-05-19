package com.schwanitz.strategies.parsing;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.io.BinaryDataReader;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.tagging.TagFormat;

import java.io.IOException;
import com.schwanitz.io.SeekableDataSource;
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
public class AIFFMetadataParsingStrategy extends AbstractTagParsingStrategy {

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



    /**
     * Erzeugt eine neue AIFF-Parsing-Strategie mit Standard-Handlern.
     */
    public AIFFMetadataParsingStrategy() {
        super("AIFF");
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
     * Parst AIFF-Metadaten aus der angegebenen Datei.
     *
     * @param format das AIFF_METADATA-Format
     * @param source die Datenquelle, aus der gelesen wird
     * @param offset der Start-Offset des AIFF-Chunks
     * @param size   die Größe des Chunks in Bytes
     * @return die extrahierten {@link GenericMetadata}
     * @throws IOException bei I/O-Fehlern oder ungültigem Chunk-Format
     */
    @Override
    public Metadata parseTag(TagFormat format, SeekableDataSource source, long offset, long size) throws IOException {
        GenericMetadata metadata = new GenericMetadata(TagFormat.AIFF_METADATA);
        parseAIFFMetadataChunk(source, metadata, offset, size);
        return metadata;
    }

    private void parseAIFFMetadataChunk(SeekableDataSource source, GenericMetadata metadata, long offset, long size)
            throws IOException {
        long pos = offset;

        // AIFF Chunk Header lesen (8 bytes)
        byte[] chunkHeader = new byte[8];
        source.readFully(pos, chunkHeader);
        pos += chunkHeader.length;

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
                parseTextChunk(source, metadata, chunkType, chunkSize, pos);
                break;

            case "COMT":
                parseCommentChunk(source, metadata, chunkSize, pos);
                break;

            case "APPL":
                parseApplicationChunk(source, metadata, chunkSize, pos);
                break;

            case "ID3 ":
                // ID3 Tags werden von separater ID3ParsingStrategy behandelt
                LOG.debug("Skipping ID3 chunk - handled by ID3ParsingStrategy");
                break;

            default:
                // Unbekannter Chunk - versuche als Text zu parsen
                if (chunkSize > 0 && chunkSize < 8192) { // Reasonable size limit
                    parseTextChunk(source, metadata, chunkType, chunkSize, pos);
                } else {
                    LOG.debug("Skipping unknown AIFF chunk: {}", chunkType);
                }
                break;
        }

        LOG.debug("Successfully parsed AIFF metadata chunk: {}", chunkType);
    }

    private void parseTextChunk(SeekableDataSource source, GenericMetadata metadata, String chunkType, int chunkSize, long pos)
            throws IOException {
        if (chunkSize <= 0) {
            return;
        }

        byte[] textData = new byte[chunkSize];
        source.readFully(pos, textData);

        // Text parsen (normalerweise US-ASCII oder UTF-8)
        String text = parseTextData(textData);

        if (!text.isEmpty()) {
            String fieldName = AIFF_CHUNKS.getOrDefault(chunkType, chunkType);
            addField(metadata, fieldName, text);

            if (LOG.isDebugEnabled()) {
                String displayText = truncateForDisplay(text, 50);
                LOG.debug("Parsed AIFF text field: {} ({}) = {}", chunkType, fieldName, displayText);
            }
        }
    }

    private void parseCommentChunk(SeekableDataSource source, GenericMetadata metadata, int chunkSize, long pos)
            throws IOException {
        if (chunkSize < 2) {
            return;
        }

        // Comment Chunk hat strukturiertes Format
        int numComments = BinaryDataReader.readBigEndianInt16(source, pos);
        pos += 2;
        int bytesRead = 2;

        for (int i = 0; i < numComments && bytesRead < chunkSize; i++) {
            if (bytesRead + 8 > chunkSize) {
                break;
            }

            // Comment Entry: timeStamp (4) + marker (2) + count (2) + text (count)
            int timeStamp = BinaryDataReader.readBigEndianInt32(source, pos);
            pos += 4;
            int marker = BinaryDataReader.readBigEndianInt16(source, pos);
            pos += 2;
            int count = BinaryDataReader.readBigEndianInt16(source, pos);
            pos += 2;
            bytesRead += 8;

            if (count > 0 && bytesRead + count <= chunkSize) {
                byte[] commentText = new byte[count];
                source.readFully(pos, commentText);
                pos += count;
                bytesRead += count;

                String text = parseTextData(commentText);
                if (!text.isEmpty()) {
                    String commentField = "Comment" + (i > 0 ? "_" + i : "");
                    addField(metadata, commentField, text + " [marker:" + marker + "]");
                }
            }

            // Padding für gerade Byte-Grenze
            if (count % 2 != 0 && bytesRead < chunkSize) {
                pos += 1;
                bytesRead++;
            }
        }
    }

    private void parseApplicationChunk(SeekableDataSource source, GenericMetadata metadata, int chunkSize, long pos)
            throws IOException {
        if (chunkSize < 4) {
            return;
        }

        // Application Signature (4 bytes)
        byte[] signature = new byte[4];
        source.readFully(pos, signature);
        pos += 4;
        String appSignature = new String(signature, StandardCharsets.US_ASCII);

        // Application Data (rest of chunk)
        int dataSize = chunkSize - 4;
        if (dataSize > 0) {
            // Für bekannte Applications spezielle Behandlung
            if ("pdos".equals(appSignature) || "stoc".equals(appSignature)) {
                // ProTools oder andere DAW-spezifische Daten
                byte[] appData = new byte[Math.min(dataSize, 256)]; // Limit for safety
                source.readFully(pos, appData);
                pos += appData.length;

                String appDataText = parseTextData(appData);
                if (!appDataText.isEmpty()) {
                    addField(metadata, "Application", appSignature + ": " + appDataText);
                }
            } else {
                // Unbekannte Application
                addField(metadata, "Application", appSignature + " [" + dataSize + " bytes]");
                pos += dataSize;
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
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 32 && c != 9 && c != 10 && c != 13) { // Kontrollzeichen außer Tab, LF, CR
                return false;
            }
        }
        return true;
    }



}