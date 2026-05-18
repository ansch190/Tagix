package com.schwanitz.strategies.parsing;

import com.schwanitz.interfaces.FieldHandler;
import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.DSFMetadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.io.BinaryDataReader;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.tagging.TagFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsing-Strategie für DSF-Metadaten (DSD Stream File).
 *
 * <p>DSF-Dateien speichern Metadaten in einem ID3-vorgelagerten Chunk ({@code ID3 }-Chunk),
 * der innerhalb der DSD-Dateistruktur liegt. Diese Strategie liest den {@code ID3 }-Chunk-Header,
 * validiert die ID3v2-Signatur und extrahiert Offset und Größe des eingebetteten ID3-Datenblocks,
 * sodass dieser von der {@link ID3ParsingStrategy} separat verarbeitet werden kann.</p>
 *
 * <p>Unterstütztes Format:</p>
 * <ul>
 *   <li>{@link TagFormat#DSF_METADATA}</li>
 * </ul>
 *
 * @see TagParsingStrategy
 * @see ID3ParsingStrategy
 */
public class DSFParsingStrategy implements TagParsingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(DSFParsingStrategy.class);

    private static final byte[] DSF_SIGNATURE = {'D', 'S', 'D', ' '};
    private static final byte[] DSF_ID3_CHUNK = {'I', 'D', '3', ' '};
    private static final int DSF_METADATA_POINTER_OFFSET = 20;
    private static final int DSF_ID3_CHUNK_HEADER_SIZE = 12;

    private final Map<String, FieldHandler<?>> handlers = new HashMap<>();

    /**
     * Erzeugt eine neue DSF-Parsing-Strategie mit einem Handler für das DSF_ID3-Feld.
     */
    public DSFParsingStrategy() {
        handlers.put("DSF_ID3", new TextFieldHandler("DSF_ID3"));
    }

    /**
     * Prüft, ob diese Strategie das angegebene Tag-Format verarbeiten kann.
     *
     * @param format das zu prüfende Tag-Format
     * @return {@code true} für {@link TagFormat#DSF_METADATA}
     */
    @Override
    public boolean canHandle(TagFormat format) {
        return format == TagFormat.DSF_METADATA;
    }

    /**
     * Parst DSF-Metadaten aus der angegebenen Datei.
     *
     * <p>Liest den {@code ID3 }-Chunk-Header am angegebenen Offset, validiert die ID3v2-Signatur
     * und speichert Offset und Größe des ID3-Datenblocks in den Metadaten zur späteren Verarbeitung.</p>
     *
     * @param format das DSF_METADATA-Format
     * @param file   die Datei, aus der gelesen wird
     * @param offset der Start-Offset des ID3-Chunks
     * @param size   die Größe des Chunks in Bytes
     * @return die extrahierten {@link DSFMetadata} mit ID3-Offset und -Größe
     * @throws IOException bei I/O-Fehlern oder ungültigem Chunk-Format
     */
    @Override
    public Metadata parseTag(TagFormat format, RandomAccessFile file, long offset, long size) throws IOException {
        DSFMetadata metadata = new DSFMetadata();

        file.seek(offset);

        byte[] chunkId = new byte[4];
        file.read(chunkId);

        if (!Arrays.equals(chunkId, DSF_ID3_CHUNK)) {
            LOG.debug("DSF metadata chunk is not an ID3 chunk at offset {}", offset);
            return metadata;
        }

        long id3ChunkSize = BinaryDataReader.readLittleEndianInt64(file);
        long id3DataOffset = offset + DSF_ID3_CHUNK_HEADER_SIZE;
        long id3DataSize = id3ChunkSize - DSF_ID3_CHUNK_HEADER_SIZE;

        if (id3DataSize <= 0 || id3DataOffset + id3DataSize > file.length()) {
            LOG.warn("Invalid DSF ID3 chunk size: {} at offset {}", id3ChunkSize, offset);
            return metadata;
        }

        byte[] id3Header = new byte[10];
        file.seek(id3DataOffset);
        int bytesRead = file.read(id3Header);
        if (bytesRead < 10 || !new String(id3Header, 0, 3, StandardCharsets.US_ASCII).equals("ID3")) {
            LOG.debug("DSF ID3 chunk does not contain valid ID3v2 header");
            return metadata;
        }

        byte version = id3Header[3];
        String versionStr = switch (version) {
            case 2 -> "ID3v2.2";
            case 3 -> "ID3v2.3";
            case 4 -> "ID3v2.4";
            default -> "ID3v2." + version;
        };

        addField(metadata, "DSF_ID3", "Contains " + versionStr + " data (" + id3DataSize + " bytes)");
        metadata.setId3DataOffset(id3DataOffset);
        metadata.setId3DataSize(id3DataSize);

        return metadata;
    }

    @SuppressWarnings("unchecked")
    private void addField(DSFMetadata metadata, String key, String value) {
        if (value == null || value.isEmpty()) return;
        FieldHandler<?> handler = handlers.get(key);
        if (handler != null) {
            metadata.addField(new MetadataField<>(key, value, (FieldHandler<String>) handler));
        } else {
            metadata.addField(new MetadataField<>(key, value, new TextFieldHandler(key)));
        }
    }

}