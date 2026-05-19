package com.schwanitz.strategies.parsing;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.DSFMetadata;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.io.BinaryDataReader;
import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.tagging.TagFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

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
public class DSFParsingStrategy extends AbstractTagParsingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(DSFParsingStrategy.class);

    private static final byte[] DSF_SIGNATURE = {'D', 'S', 'D', ' '};
    private static final byte[] DSF_ID3_CHUNK = {'I', 'D', '3', ' '};
    private static final int DSF_METADATA_POINTER_OFFSET = 20;
    private static final int DSF_ID3_CHUNK_HEADER_SIZE = 12;



    /**
     * Erzeugt eine neue DSF-Parsing-Strategie mit einem Handler für das DSF_ID3-Feld.
     */
    public DSFParsingStrategy() {
        super("DSF");
        handlers.put("DSF_ID3", new TextFieldHandler("DSF_ID3"));
    }

    /**
     * Parst DSF-Metadaten aus der angegebenen Datenquelle.
     *
     * <p>Liest den {@code ID3 }-Chunk-Header am angegebenen Offset, validiert die ID3v2-Signatur
     * und speichert Offset und Größe des ID3-Datenblocks in den Metadaten zur späteren Verarbeitung.</p>
     *
     * @param format das DSF_METADATA-Format
     * @param source die Datenquelle, aus der gelesen wird
     * @param offset der Start-Offset des ID3-Chunks
     * @param size   die Größe des Chunks in Bytes
     * @return die extrahierten {@link DSFMetadata} mit ID3-Offset und -Größe
     * @throws IOException bei I/O-Fehlern oder ungültigem Chunk-Format
     */
    @Override
    public Metadata parseTag(TagFormat format, SeekableDataSource source, long offset, long size) throws IOException {
        DSFMetadata metadata = new DSFMetadata();

        byte[] chunkId = new byte[4];
        source.readFully(offset, chunkId);

        if (!Arrays.equals(chunkId, DSF_ID3_CHUNK)) {
            LOG.debug("DSF metadata chunk is not an ID3 chunk at offset {}", offset);
            return metadata;
        }

        long id3ChunkSize = BinaryDataReader.readLittleEndianInt64(source, offset + 4);
        long id3DataOffset = offset + DSF_ID3_CHUNK_HEADER_SIZE;
        long id3DataSize = id3ChunkSize - DSF_ID3_CHUNK_HEADER_SIZE;

        if (id3DataSize <= 0 || id3DataOffset + id3DataSize > source.length()) {
            LOG.warn("Invalid DSF ID3 chunk size: {} at offset {}", id3ChunkSize, offset);
            return metadata;
        }

        byte[] id3Header = new byte[10];
        source.readFully(id3DataOffset, id3Header);
        if (!new String(id3Header, 0, 3, StandardCharsets.US_ASCII).equals("ID3")) {
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

        addField(metadata, "DSF_ID3", "Contains " + versionStr + " data (" + id3DataSize + " bytes)", true, false, false);
        metadata.setId3DataOffset(id3DataOffset);
        metadata.setId3DataSize(id3DataSize);

        return metadata;
    }


}
