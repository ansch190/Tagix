package com.schwanitz.strategies.parsing;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.tagging.TagFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
public class FLACApplicationParsingStrategy extends AbstractTagParsingStrategy {

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



    /**
     * Erzeugt eine neue FLAC-Application-Parsing-Strategie mit Standard-Handlern.
     */
    public FLACApplicationParsingStrategy() {
        super("FLAC");
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
    public Metadata parseTag(TagFormat format, SeekableDataSource source, long offset, long size) throws IOException {
        GenericMetadata metadata = new GenericMetadata(TagFormat.FLAC_APPLICATION);

        byte[] blockHeader = new byte[FLAC_BLOCK_HEADER_SIZE];
        source.readFully(offset, blockHeader);

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
        source.readFully(offset + FLAC_BLOCK_HEADER_SIZE, appIdBytes);
        String appIdAscii = new String(appIdBytes, StandardCharsets.US_ASCII);

        int appIdInt = ((appIdBytes[0] & 0xFF) << 24) |
                ((appIdBytes[1] & 0xFF) << 16) |
                ((appIdBytes[2] & 0xFF) << 8) |
                (appIdBytes[3] & 0xFF);

        String appName = KNOWN_APPLICATION_IDS.getOrDefault(appIdInt,
                String.format("Unknown (0x%08X)", appIdInt));

        addField(metadata, "ApplicationId", appName + " [" + appIdAscii + "]", true, false, false);

        int dataSize = blockLength - FLAC_APPLICATION_ID_SIZE;
        if (dataSize > 0) {
            addField(metadata, "ApplicationData", dataSize + " bytes of application data", true, false, false);
        }

        return metadata;
    }


}