package com.schwanitz.strategies.parsing;

import com.schwanitz.interfaces.FieldHandler;
import com.schwanitz.interfaces.Metadata;
import com.schwanitz.others.MetadataField;
import com.schwanitz.others.TextFieldHandler;
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

public class AIFFMetadataParsingStrategy implements TagParsingStrategy {

    private static final Logger Log = LoggerFactory.getLogger(AIFFMetadataParsingStrategy.class);

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

    @Override
    public boolean canHandle(TagFormat format) {
        return format == TagFormat.AIFF_METADATA;
    }

    @Override
    public Metadata parseTag(TagFormat format, RandomAccessFile file, long offset, long size) throws IOException {
        AIFFMetadata metadata = new AIFFMetadata();
        parseAIFFMetadataChunk(file, metadata, offset, size);
        return metadata;
    }

    private void parseAIFFMetadataChunk(RandomAccessFile file, AIFFMetadata metadata, long offset, long size)
            throws IOException {
        file.seek(offset);

        // AIFF Chunk Header lesen (8 bytes)
        byte[] chunkHeader = new byte[8];
        file.read(chunkHeader);

        String chunkType = new String(chunkHeader, 0, 4, StandardCharsets.US_ASCII);
        int chunkSize = readBigEndianInt32(chunkHeader, 4);

        Log.debug("Parsing AIFF chunk: " + chunkType + " with size: " + chunkSize);

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
                Log.debug("Skipping ID3 chunk - handled by ID3ParsingStrategy");
                break;

            default:
                // Unbekannter Chunk - versuche als Text zu parsen
                if (chunkSize > 0 && chunkSize < 8192) { // Reasonable size limit
                    parseTextChunk(file, metadata, chunkType, chunkSize);
                } else {
                    Log.debug("Skipping unknown AIFF chunk: " + chunkType);
                }
                break;
        }

        Log.debug("Successfully parsed AIFF metadata chunk: " + chunkType);
    }

    private void parseTextChunk(RandomAccessFile file, AIFFMetadata metadata, String chunkType, int chunkSize)
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

            Log.debug("Parsed AIFF text field: " + chunkType + " (" + fieldName + ") = " +
                    (text.length() > 50 ? text.substring(0, 50) + "..." : text));
        }
    }

    private void parseCommentChunk(RandomAccessFile file, AIFFMetadata metadata, int chunkSize)
            throws IOException {
        if (chunkSize < 2) {
            return;
        }

        // Comment Chunk hat strukturiertes Format
        int numComments = readBigEndianInt16(file);
        int bytesRead = 2;

        for (int i = 0; i < numComments && bytesRead < chunkSize; i++) {
            if (bytesRead + 8 > chunkSize) {
                break;
            }

            // Comment Entry: timeStamp (4) + marker (2) + count (2) + text (count)
            int timeStamp = readBigEndianInt32(file);
            int marker = readBigEndianInt16(file);
            int count = readBigEndianInt16(file);
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

    private void parseApplicationChunk(RandomAccessFile file, AIFFMetadata metadata, int chunkSize)
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

    private int readBigEndianInt16(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[2];
        file.read(bytes);
        return ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
    }

    private int readBigEndianInt32(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[4];
        file.read(bytes);
        return ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16) |
                ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
    }

    private int readBigEndianInt32(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16) |
                ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }

    @SuppressWarnings("unchecked")
    private void addField(AIFFMetadata metadata, String key, String value) {
        FieldHandler<?> handler = handlers.get(key);
        if (handler != null) {
            metadata.addField(new MetadataField<>(key, value, (FieldHandler<String>) handler));
        } else {
            TextFieldHandler textHandler = new TextFieldHandler(key);
            metadata.addField(new MetadataField<>(key, value, textHandler));
            Log.debug("Created fallback handler for unknown AIFF field: " + key);
        }
    }

    public void registerHandler(String key, FieldHandler<?> handler) {
        handlers.put(key, handler);
    }

    // Innere Klasse für AIFF Metadata
    public static class AIFFMetadata implements Metadata {
        private final List<MetadataField<?>> fields = new ArrayList<>();

        @Override
        public String getTagFormat() {
            return TagFormat.AIFF_METADATA.getFormatName();
        }

        @Override
        public List<MetadataField<?>> getFields() {
            return fields;
        }

        @Override
        public void addField(MetadataField<?> field) {
            fields.add(field);
        }
    }
}