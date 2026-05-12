package com.schwanitz.strategies.parsing;

import com.schwanitz.interfaces.FieldHandler;
import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.tagging.TagFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsing-Strategie für FLAC-Application-Metadaten-Blöcke.
 *
 * <p>FLAC-Dateien können Application-spezifische Metadaten in speziellen METADATA_BLOCK-Strukturen
 * speichern. Ein Application-Block enthält eine 4-Byte-ID (regstriert bei FLAC) gefolgt von
 * anwendungspezifischen Daten. Diese Strategie liest den Block-Header, validiert den Block-Typ
 * (0x02 = APPLICATION) und extrahiert die Application-ID sowie die Datenlänge.</p>
 *
 * <p>Bekannte Application-IDs sind in {@link #KNOWN_APPLICATION_IDS} hinterlegt.</p>
 *
 * <p>Unterstütztes Format:</p>
 * <ul>
 *   <li>{@link TagFormat#FLAC_APPLICATION}</li>
 * </ul>
 *
 * @see TagParsingStrategy
 */
public class FLACApplicationParsingStrategy implements TagParsingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(FLACApplicationParsingStrategy.class);

    private static final int FLAC_BLOCK_HEADER_SIZE = 4;
    private static final int FLAC_APPLICATION_ID_SIZE = 4;
    private static final int FLAC_APPLICATION_BLOCK_TYPE = 2;
    private static final int FLAC_SIGNATURE_LENGTH = 4;
    private static final int FLAC_LAST_BLOCK_FLAG = 0x80;
    private static final int FLAC_BLOCK_TYPE_MASK = 0x7F;
    private static final int MAX_BLOCKS = 1000;

    private static final Map<Integer, String> KNOWN_APPLICATION_IDS = new HashMap<>();

    static {
        KNOWN_APPLICATION_IDS.put(0x41544348, "ATCH");
        KNOWN_APPLICATION_IDS.put(0x42534F4C, "BSOL");
        KNOWN_APPLICATION_IDS.put(0x46696361, "Fica");
        KNOWN_APPLICATION_IDS.put(0x52414741, "RAGA");
        KNOWN_APPLICATION_IDS.put(0x55554944, "UUID");
    }

    private final Map<String, FieldHandler<?>> handlers = new HashMap<>();

    /**
     * Erzeugt eine neue FLAC-Application-Parsing-Strategie mit Standard-Handlern.
     */
    public FLACApplicationParsingStrategy() {
        handlers.put("ApplicationId", new TextFieldHandler("ApplicationId"));
        handlers.put("ApplicationData", new TextFieldHandler("ApplicationData"));
    }

    /**
     * Prüft, ob diese Strategie das angegebene Tag-Format verarbeiten kann.
     *
     * @param format das zu prüfende Tag-Format
     * @return {@code true} für {@link TagFormat#FLAC_APPLICATION}
     */
    @Override
    public boolean canHandle(TagFormat format) {
        return format == TagFormat.FLAC_APPLICATION;
    }

    /**
     * Parst einen FLAC-Application-Metadaten-Block aus der angegebenen Datei.
     *
     * <p>Liest den 4-Byte-Block-Header, validiert den Block-Typ, extrahiert die Application-ID
     * und speichert die Datengröße in den Metadaten.</p>
     *
     * @param format das FLAC_APPLICATION-Format
     * @param file   die Datei, aus der gelesen wird
     * @param offset der Start-Offset des Application-Blocks
     * @param size   die Größe des Blocks in Bytes
     * @return die extrahierten {@link FLACApplicationMetadata}
     * @throws IOException bei I/O-Fehlern oder wenn der Block-Typ nicht APPLICATION entspricht
     */
    @Override
    public Metadata parseTag(TagFormat format, RandomAccessFile file, long offset, long size) throws IOException {
        FLACApplicationMetadata metadata = new FLACApplicationMetadata();

        file.seek(offset);

        byte[] blockHeader = new byte[FLAC_BLOCK_HEADER_SIZE];
        file.read(blockHeader);

        int blockType = blockHeader[0] & FLAC_BLOCK_TYPE_MASK;
        int blockLength = ((blockHeader[1] & 0xFF) << 16) |
                ((blockHeader[2] & 0xFF) << 8) |
                (blockHeader[3] & 0xFF);

        if (blockType != FLAC_APPLICATION_BLOCK_TYPE) {
            LOG.debug("Not a FLAC APPLICATION block at offset {}", offset);
            return metadata;
        }

        if (blockLength < FLAC_APPLICATION_ID_SIZE) {
            LOG.warn("FLAC APPLICATION block too short at offset {}", offset);
            return metadata;
        }

        byte[] appIdBytes = new byte[FLAC_APPLICATION_ID_SIZE];
        file.read(appIdBytes);
        String appIdAscii = new String(appIdBytes, StandardCharsets.US_ASCII);

        int appIdInt = ((appIdBytes[0] & 0xFF) << 24) |
                ((appIdBytes[1] & 0xFF) << 16) |
                ((appIdBytes[2] & 0xFF) << 8) |
                (appIdBytes[3] & 0xFF);

        String appName = KNOWN_APPLICATION_IDS.getOrDefault(appIdInt,
                String.format("Unknown (0x%08X)", appIdInt));

        addField(metadata, "ApplicationId", appName + " [" + appIdAscii + "]");

        int dataSize = blockLength - FLAC_APPLICATION_ID_SIZE;
        if (dataSize > 0) {
            addField(metadata, "ApplicationData", dataSize + " bytes of application data");
        }

        return metadata;
    }

    @SuppressWarnings("unchecked")
    private void addField(FLACApplicationMetadata metadata, String key, String value) {
        if (value == null || value.isEmpty()) return;
        FieldHandler<?> handler = handlers.get(key);
        if (handler != null) {
            metadata.addField(new MetadataField<>(key, value, (FieldHandler<String>) handler));
        } else {
            metadata.addField(new MetadataField<>(key, value, new TextFieldHandler(key)));
        }
    }

    /**
     * Innere Klasse für FLAC-Application-spezifische Metadaten.
     *
     * <p>Hält die Liste der extrahierten {@link MetadataField}-Objekte und gibt als Format {@code "FLAC_APPLICATION"} zurück.</p>
     */
    public static class FLACApplicationMetadata implements Metadata {
        private final List<MetadataField<?>> fields = new ArrayList<>();

        @Override
        public String getTagFormat() {
            return TagFormat.FLAC_APPLICATION.getFormatName();
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