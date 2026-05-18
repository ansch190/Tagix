package com.schwanitz.strategies.parsing;

import com.schwanitz.interfaces.FieldHandler;
import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.tagging.TagFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsing-Strategie für WavPack-Metadaten-Subblöcke.
 *
 * <p>WavPack-Dateien verwenden Metadaten-Subblöcke innerhalb des Block-Headers, um zusätzliche
 * Informationen wie RIFF-Header, MD5-Prüfsummen und Konfigurationsdaten zu speichern. Jeder Subblock
 * hat einen 1-Byte-Header mit ID und Größenflag, gefolgt von den Daten. Diese Strategie liest
 * den Subblock-Header, identifiziert den Typ und extrahiertformatierte Werte wie MD5-Hashes.</p>
 *
 * <p>Unterstütztes Format:</p>
 * <ul>
 *   <li>{@link TagFormat#WAVPACK_NATIVE}</li>
 * </ul>
 *
 * @see TagParsingStrategy
 */
public class WavPackParsingStrategy implements TagParsingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(WavPackParsingStrategy.class);

    private static final byte[] WAVPACK_SIGNATURE = {'w', 'v', 'p', 'k'};
    private static final int WAVPACK_HEADER_SIZE = 32;
    private static final int SUBBLOCK_LARGE_FLAG = 0x80;
    private static final int SUBBLOCK_ID_MASK = 0x7F;

    private static final Map<Integer, String> METADATA_SUBBLOCKS = new HashMap<>();

    static {
        METADATA_SUBBLOCKS.put(0x21, "RIFFHeader");
        METADATA_SUBBLOCKS.put(0x22, "RIFFTrailer");
        METADATA_SUBBLOCKS.put(0x23, "AlternativeHeader");
        METADATA_SUBBLOCKS.put(0x24, "AlternativeTrailer");
        METADATA_SUBBLOCKS.put(0x25, "ConfigurationBlock");
        METADATA_SUBBLOCKS.put(0x26, "MD5Checksum");
    }

    private final Map<String, FieldHandler<?>> handlers = new HashMap<>();

    /**
     * Erzeugt eine neue WavPack-Parsing-Strategie mit Standard-Handlern für bekannte Subblock-Typen.
     */
    public WavPackParsingStrategy() {
        for (String fieldName : METADATA_SUBBLOCKS.values()) {
            handlers.put(fieldName, new TextFieldHandler(fieldName));
        }
    }

    /**
     * Prüft, ob diese Strategie das angegebene Tag-Format verarbeiten kann.
     *
     * @param format das zu prüfende Tag-Format
     * @return {@code true} für {@link TagFormat#WAVPACK_NATIVE}
     */
    @Override
    public boolean canHandle(TagFormat format) {
        return format == TagFormat.WAVPACK_NATIVE;
    }

    /**
     * Parst einen WavPack-Metadaten-Subblock aus der angegebenen Datei.
     *
     * @param format das WAVPACK_NATIVE-Format
     * @param file   die Datei, aus der gelesen wird
     * @param offset der Start-Offset des Subblocks
     * @param size   die verfügbare Größe in Bytes
     * @return die extrahierten {@link GenericMetadata}
     * @throws IOException bei I/O-Fehlern
     */
    @Override
    public Metadata parseTag(TagFormat format, RandomAccessFile file, long offset, long size) throws IOException {
        GenericMetadata metadata = new GenericMetadata(TagFormat.WAVPACK_NATIVE);

        file.seek(offset);

        byte[] subBlockHeader = new byte[1];
        file.read(subBlockHeader);

        int subBlockIdRaw = subBlockHeader[0] & 0xFF;
        int cleanId = subBlockIdRaw & SUBBLOCK_ID_MASK;
        String blockName = METADATA_SUBBLOCKS.getOrDefault(cleanId, "Unknown_" + cleanId);

        int subBlockSize;
        int headerSize;

        if ((subBlockIdRaw & SUBBLOCK_LARGE_FLAG) != 0) {
            byte[] sizeBytes = new byte[3];
            file.read(sizeBytes);
            subBlockSize = (sizeBytes[0] & 0xFF) |
                    ((sizeBytes[1] & 0xFF) << 8) |
                    ((sizeBytes[2] & 0xFF) << 16);
            headerSize = 4;
        } else {
            int sizeByte = file.read();
            if (sizeByte == -1) {
                return metadata;
            }
            subBlockSize = sizeByte << 1;
            headerSize = 2;
        }

        if (subBlockSize > 0 && subBlockSize <= size - headerSize) {
            byte[] data = new byte[Math.min(subBlockSize, 65536)];
            file.read(data);

            String subBlockName = METADATA_SUBBLOCKS.get(cleanId);
            if (subBlockName != null) {
                addField(metadata, subBlockName, formatSubBlockValue(cleanId, data));
            } else {
                addField(metadata, blockName, "[" + subBlockSize + " bytes]");
            }
        }

        return metadata;
    }

    private String formatSubBlockValue(int blockId, byte[] data) {
        return switch (blockId) {
            case 0x26 -> formatMD5(data);
            case 0x25 -> formatConfiguration(data);
            default -> "[" + data.length + " bytes of metadata]";
        };
    }

    private String formatMD5(byte[] data) {
        if (data.length < 16) return "[incomplete MD5]";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            sb.append(String.format("%02x", data[i] & 0xFF));
        }
        return sb.toString();
    }

    private String formatConfiguration(byte[] data) {
        return data.length + " bytes of configuration data";
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