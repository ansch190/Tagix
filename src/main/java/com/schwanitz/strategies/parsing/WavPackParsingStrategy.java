package com.schwanitz.strategies.parsing;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.TextFieldHandler;
import static com.schwanitz.formats.wavpack.WavPackConstants.*;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.io.SourceReader;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.tagging.TagFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
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
public class WavPackParsingStrategy extends AbstractTagParsingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(WavPackParsingStrategy.class);



    private static final Map<Integer, String> METADATA_SUBBLOCKS = new HashMap<>();

    static {
        METADATA_SUBBLOCKS.put(0x21, "RIFFHeader");
        METADATA_SUBBLOCKS.put(0x22, "RIFFTrailer");
        METADATA_SUBBLOCKS.put(0x23, "AlternativeHeader");
        METADATA_SUBBLOCKS.put(0x24, "AlternativeTrailer");
        METADATA_SUBBLOCKS.put(0x25, "ConfigurationBlock");
        METADATA_SUBBLOCKS.put(0x26, "MD5Checksum");
    }



    /**
     * Erzeugt eine neue WavPack-Parsing-Strategie mit Standard-Handlern für bekannte Subblock-Typen.
     */
    public WavPackParsingStrategy() {
        super("WavPack");
        for (String fieldName : METADATA_SUBBLOCKS.values()) {
            handlers.put(fieldName, new TextFieldHandler(fieldName));
        }
    }

    /**
     * Parst einen WavPack-Metadaten-Subblock aus der angegebenen Datenquelle.
     *
     * @param format das WAVPACK_NATIVE-Format
     * @param source die Datenquelle, aus der gelesen wird
     * @param offset der Start-Offset des Subblocks
     * @param size   die verfügbare Größe in Bytes
     * @return die extrahierten {@link GenericMetadata}
     * @throws IOException bei I/O-Fehlern
     */
    @Override
    public Metadata parseTag(TagFormat format, SeekableDataSource source, long offset, long size) throws IOException {
        GenericMetadata metadata = new GenericMetadata(TagFormat.WAVPACK_NATIVE);

        SourceReader reader = new SourceReader(source, offset);

        byte[] subBlockHeader = new byte[1];
        reader.read(subBlockHeader);

        int subBlockIdRaw = subBlockHeader[0] & 0xFF;
        int cleanId = subBlockIdRaw & SUBBLOCK_ID_MASK;
        String blockName = METADATA_SUBBLOCKS.getOrDefault(cleanId, "Unknown_" + cleanId);

        int subBlockSize;
        int headerSize;

        if ((subBlockIdRaw & SUBBLOCK_LARGE_FLAG) != 0) {
            byte[] sizeBytes = new byte[3];
            reader.read(sizeBytes);
            subBlockSize = (sizeBytes[0] & 0xFF) |
                    ((sizeBytes[1] & 0xFF) << 8) |
                    ((sizeBytes[2] & 0xFF) << 16);
            headerSize = 4;
        } else {
            int sizeByte = reader.read();
            if (sizeByte == -1) {
                return metadata;
            }
            subBlockSize = sizeByte << 1;
            headerSize = 2;
        }

        if (subBlockSize > 0 && subBlockSize <= size - headerSize) {
            byte[] data = new byte[Math.min(subBlockSize, 65536)];
            reader.read(data);

            String subBlockName = METADATA_SUBBLOCKS.get(cleanId);
            if (subBlockName != null) {
                addField(metadata, subBlockName, formatSubBlockValue(cleanId, data), true, false, false);
            } else {
                addField(metadata, blockName, "[" + subBlockSize + " bytes]", true, false, false);
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


}
