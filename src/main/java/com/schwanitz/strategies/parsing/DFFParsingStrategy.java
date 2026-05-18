package com.schwanitz.strategies.parsing;

import com.schwanitz.interfaces.FieldHandler;
import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.GenericMetadata;
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
 * Parsing-Strategie für DFF-Metadaten (DSD Interchange File Format / DSDIFF).
 *
 * <p>DSDIFF-Dateien verwenden eine IFF-ähnliche Chunk-Struktur mit Big-Endian-Größenangaben.
 * Metadaten werden im {@code DIIN}-Chunk gespeichert, der Sub-Chunks wie {@code DITI} (Titel),
 * {@code DIAR} (Künstler), {@code DIAL} (Album) und weitere Textfelder enthalten kann.
 * Diese Strategie navigiert durch die Chunk-Hierarchie und extrahiert alle bekannten
 * DSDIFF-Metadatenfelder.</p>
 *
 * <p>Unterstütztes Format:</p>
 * <ul>
 *   <li>{@link TagFormat#DFF_METADATA}</li>
 * </ul>
 *
 * @see TagParsingStrategy
 */
public class DFFParsingStrategy implements TagParsingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(DFFParsingStrategy.class);

    private static final byte[] DFF_FORM_SIGNATURE = {'F', 'R', 'M', '8'};
    private static final byte[] DFF_DSD_TYPE = {'D', 'S', 'D', ' '};
    private static final byte[] DFF_DIIN_CHUNK = {'D', 'I', 'I', 'N'};
    private static final byte[] DFF_DITI_CHUNK = {'D', 'I', 'T', 'I'};
    private static final int DFF_CHUNK_HEADER_SIZE = 12;

    private static final Map<String, String> DFF_FIELD_MAPPING = new HashMap<>();

    static {
        DFF_FIELD_MAPPING.put("DSD ", "Format");
        DFF_FIELD_MAPPING.put("DITI", "Title");
        DFF_FIELD_MAPPING.put("DIAR", "Artist");
        DFF_FIELD_MAPPING.put("DIAL", "Album");
        DFF_FIELD_MAPPING.put("DICN", "Comment");
        DFF_FIELD_MAPPING.put("DIRC", "Copyright");
        DFF_FIELD_MAPPING.put("DIDT", "Date");
        DFF_FIELD_MAPPING.put("DIGR", "Genre");
        DFF_FIELD_MAPPING.put("DIBT", "TrackNumber");
    }

    private final Map<String, FieldHandler<?>> handlers = new HashMap<>();

    /**
     * Erzeugt eine neue DFF-Parsing-Strategie mit Standard-Handlern für alle bekannten DSDIFF-Felder.
     */
    public DFFParsingStrategy() {
        for (String fieldName : DFF_FIELD_MAPPING.values()) {
            handlers.put(fieldName, new TextFieldHandler(fieldName));
        }
    }

    /**
     * Prüft, ob diese Strategie das angegebene Tag-Format verarbeiten kann.
     *
     * @param format das zu prüfende Tag-Format
     * @return {@code true} für {@link TagFormat#DFF_METADATA}
     */
    @Override
    public boolean canHandle(TagFormat format) {
        return format == TagFormat.DFF_METADATA;
    }

    /**
     * Parst DFF-Metadaten aus der angegebenen Datei.
     *
     * <p>Erkennt den Chunk-Typ am Offset ({@code DIIN} oder {@code DITI}) und delegiert
     * an die entsprechende Parse-Methode.</p>
     *
     * @param format das DFF_METADATA-Format
     * @param file   die Datei, aus der gelesen wird
     * @param offset der Start-Offset des Chunks
     * @param size   die Größe des Chunks in Bytes
     * @return die extrahierten {@link GenericMetadata}
     * @throws IOException bei I/O-Fehlern oder ungültigem Chunk-Format
     */
    @Override
    public Metadata parseTag(TagFormat format, RandomAccessFile file, long offset, long size) throws IOException {
        GenericMetadata metadata = new GenericMetadata(TagFormat.DFF_METADATA);

        file.seek(offset);

        byte[] chunkId = new byte[4];
        file.read(chunkId);
        long chunkSize = BinaryDataReader.readBigEndianInt64(file);

        if (Arrays.equals(chunkId, DFF_DIIN_CHUNK)) {
            parseDIINChunk(file, metadata, offset, chunkSize);
        } else if (Arrays.equals(chunkId, DFF_DITI_CHUNK)) {
            parseDITIChunk(file, metadata, chunkSize);
        } else {
            LOG.debug("Unknown DFF metadata chunk: {} at offset {}", new String(chunkId, StandardCharsets.US_ASCII), offset);
        }

        return metadata;
    }

    private void parseDIINChunk(RandomAccessFile file, GenericMetadata metadata, long offset, long chunkSize) throws IOException {
        long endPos = offset + DFF_CHUNK_HEADER_SIZE + chunkSize;
        long currentPos = offset + DFF_CHUNK_HEADER_SIZE;

        while (currentPos + DFF_CHUNK_HEADER_SIZE < endPos) {
            file.seek(currentPos);

            byte[] subChunkId = new byte[4];
            file.read(subChunkId);

            long subChunkSize = BinaryDataReader.readBigEndianInt64(file);

            if (subChunkSize < 0 || currentPos + DFF_CHUNK_HEADER_SIZE + subChunkSize > endPos) {
                break;
            }

            String chunkName = new String(subChunkId, StandardCharsets.US_ASCII);

            if (Arrays.equals(subChunkId, DFF_DITI_CHUNK)) {
                parseDITIChunk(file, metadata, subChunkSize);
            } else if (DFF_FIELD_MAPPING.containsKey(chunkName)) {
                parseTextSubChunk(file, metadata, subChunkId, subChunkSize);
            }

            currentPos += DFF_CHUNK_HEADER_SIZE + subChunkSize;
            if (subChunkSize % 2 != 0) {
                currentPos++;
            }
        }
    }

    private void parseDITIChunk(RandomAccessFile file, GenericMetadata metadata, long chunkSize) throws IOException {
        if (chunkSize <= 0) return;

        byte[] data = new byte[(int)Math.min(chunkSize, 65536)];
        file.read(data);
        String title = new String(data, StandardCharsets.UTF_8).trim();

        int nullIdx = title.indexOf('\0');
        if (nullIdx >= 0) title = title.substring(0, nullIdx);

        if (!title.isEmpty()) {
            addField(metadata, "Title", title);
        }
    }

    private void parseTextSubChunk(RandomAccessFile file, GenericMetadata metadata, byte[] chunkId, long chunkSize) throws IOException {
        if (chunkSize <= 0) return;

        String chunkName = new String(chunkId, StandardCharsets.US_ASCII);
        String fieldName = DFF_FIELD_MAPPING.getOrDefault(chunkName, chunkName);

        byte[] data = new byte[(int)Math.min(chunkSize, 65536)];
        file.read(data);
        String text = new String(data, StandardCharsets.UTF_8).trim();

        int nullIdx = text.indexOf('\0');
        if (nullIdx >= 0) text = text.substring(0, nullIdx);

        if (!text.isEmpty()) {
            addField(metadata, fieldName, text);
        }
    }

    @SuppressWarnings("unchecked")
    private void addField(GenericMetadata metadata, String key, String value) {
        if (value == null || value.isEmpty()) return;
        FieldHandler<?> handler = handlers.get(key);
        if (handler != null) {
            metadata.addField(new MetadataField<>(key, value, (FieldHandler<String>) handler));
        } else {
            metadata.addField(new MetadataField<>(key, value, new TextFieldHandler(key)));
        }
    }

}