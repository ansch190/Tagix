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
 * Parsing-Strategie für RIFF-INFO-Metadaten in WAV-Dateien.
 *
 * <p>Das RIFF-INFO-Format speichert Metadaten als LIST-Chunk des Typs „INFO" innerhalb
 * einer RIFF/WAV-Datei. Jedes Feld ist ein Sub-Chunk mit einem 4-Zeichen-Identifier (z.&nbsp;B.
 * {@code INAM} für Titel, {@code IART} für Künstler), gefolgt von einem Little-Endian-Größenwort
 * und dem null-terminierten Textwert. Diese Strategie navigiert durch die Sub-Chunks und
 * extrahiert alle bekannten RIFF-INFO-Felder als {@link RIFFInfoMetadata}.</p>
 *
 * <p>Unterstütztes Format:</p>
 * <ul>
 *   <li>{@link TagFormat#RIFF_INFO}</li>
 * </ul>
 *
 * @see TagParsingStrategy
 */
public class RIFFInfoParsingStrategy extends AbstractTagParsingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(RIFFInfoParsingStrategy.class);

    // RIFF INFO Chunk IDs (4 Zeichen)
    private static final Map<String, String> INFO_CHUNKS = new HashMap<>();

    static {
        // Audio-relevante Kern-Metadaten
        INFO_CHUNKS.put("INAM", "Title");            // Name/Title
        INFO_CHUNKS.put("IART", "Artist");           // Artist
        INFO_CHUNKS.put("IPRD", "Album");            // Product (Album)
        INFO_CHUNKS.put("ICRD", "Date");             // Creation Date
        INFO_CHUNKS.put("IGNR", "Genre");            // Genre
        INFO_CHUNKS.put("ICMT", "Comment");          // Comment
        INFO_CHUNKS.put("ICOP", "Copyright");        // Copyright

        // Produktions-Metadaten
        INFO_CHUNKS.put("IENG", "Engineer");         // Engineer
        INFO_CHUNKS.put("ITCH", "Technician");       // Technician
        INFO_CHUNKS.put("ISFT", "Software");         // Software
        INFO_CHUNKS.put("IPRO", "Producer");         // Producer
        INFO_CHUNKS.put("ICMS", "Commissioned");     // Commissioned

        // Quelle/Medium
        INFO_CHUNKS.put("IMED", "Medium");           // Medium
        INFO_CHUNKS.put("ISRC", "Source");           // Source
        INFO_CHUNKS.put("ISRF", "SourceForm");       // Source Form
        INFO_CHUNKS.put("IARL", "Archival");         // Archival Location

        // Inhalt/Beschreibung
        INFO_CHUNKS.put("ISBJ", "Subject");          // Subject
        INFO_CHUNKS.put("IKEY", "Keywords");         // Keywords
        INFO_CHUNKS.put("ISTR", "Starring");         // Starring
        INFO_CHUNKS.put("IPRT", "Part");             // Part

        // Technische Daten
        INFO_CHUNKS.put("ITRK", "TrackNumber");      // Track Number
        INFO_CHUNKS.put("IFRM", "TotalFrames");      // Total Frames
        INFO_CHUNKS.put("IMUS", "Musician");         // Musician

        // Erweiterte Chunks
        INFO_CHUNKS.put("ISMP", "SMPTETimeCode");    // SMPTE Time Code
        INFO_CHUNKS.put("IDIT", "DigitizationDate"); // Digitization Date
        INFO_CHUNKS.put("IWRI", "WrittenBy");        // Written By

        // Veraltete Chunks (für Kompatibilität beibehalten)
        INFO_CHUNKS.put("IDOS", "DOSName");          // DOS Name (veraltet)
    }



    /**
     * Erzeugt eine neue RIFF-INFO-Parsing-Strategie mit Standard-Handlern.
     */
    public RIFFInfoParsingStrategy() {
        super("RIFF");
        initializeDefaultHandlers();
    }

    private void initializeDefaultHandlers() {
        // Handler für alle bekannten RIFF INFO Felder erstellen
        for (String fieldName : INFO_CHUNKS.values()) {
            handlers.put(fieldName, new TextFieldHandler(fieldName));
        }
    }

    /**
     * Parst einen RIFF-INFO-Chunk aus der angegebenen Datei.
     *
     * @param format das RIFF_INFO-Format
     * @param source die Datenquelle, aus der gelesen wird
     * @param offset der Start-Offset des LIST-Chunks
     * @param size   die Größe des Chunks in Bytes
     * @return die extrahierten {@link GenericMetadata}
     * @throws IOException bei I/O-Fehlern oder wenn der Chunk keine gültige INFO-Struktur enthält
     */
    @Override
    public Metadata parseTag(TagFormat format, SeekableDataSource source, long offset, long size) throws IOException {
        GenericMetadata metadata = new GenericMetadata(TagFormat.RIFF_INFO);
        parseRIFFInfoChunk(source, metadata, offset, size);
        return metadata;
    }

    private void parseRIFFInfoChunk(SeekableDataSource source, GenericMetadata metadata, long offset, long size)
            throws IOException {
        long pos = offset;

        // LIST Chunk Header lesen
        byte[] listHeader = new byte[8];
        source.readFully(pos, listHeader);
        pos += listHeader.length;

        String chunkId = new String(listHeader, 0, 4, StandardCharsets.US_ASCII);
        if (!"LIST".equals(chunkId)) {
            throw new IOException("Expected LIST chunk, found: " + chunkId);
        }

        int chunkSize = BinaryDataReader.readLittleEndianInt32(listHeader, 4);
        if (chunkSize < 4) {
            throw new IOException("Invalid LIST chunk size: " + chunkSize);
        }

        // INFO Identifier lesen
        byte[] infoId = new byte[4];
        source.readFully(pos, infoId);
        pos += infoId.length;
        String infoType = new String(infoId, StandardCharsets.US_ASCII);

        if (!"INFO".equals(infoType)) {
            throw new IOException("Expected INFO list type, found: " + infoType);
        }

        LOG.debug("Parsing RIFF INFO chunk with size: {}", chunkSize);

        // INFO Sub-Chunks parsen
        long currentPos = offset + 12; // 8 bytes LIST header + 4 bytes INFO type
        long endPos = offset + 8 + chunkSize; // 8 bytes LIST header + chunk size

        int fieldCount = 0;
        while (currentPos < endPos - 8) { // Mindestens 8 Bytes für Sub-Chunk Header
            pos = currentPos;

            byte[] subChunkHeader = new byte[8];
            source.readFully(pos, subChunkHeader);
            pos += subChunkHeader.length;

            String subChunkId = new String(subChunkHeader, 0, 4, StandardCharsets.US_ASCII);
            int subChunkSize = BinaryDataReader.readLittleEndianInt32(subChunkHeader, 4);

            if (subChunkSize < 0 || subChunkSize > endPos - currentPos - 8) {
                LOG.warn("Invalid sub-chunk size for {}: {}", subChunkId, subChunkSize);
                break;
            }

            // Sub-Chunk Daten lesen
            if (subChunkSize > 0) {
                byte[] subChunkData = new byte[subChunkSize];
                source.readFully(pos, subChunkData);
                pos += subChunkData.length;

                // Null-terminated String parsen
                String value = parseNullTerminatedString(subChunkData);

                if (!value.isEmpty()) {
                    String fieldName = INFO_CHUNKS.getOrDefault(subChunkId, subChunkId);
                    addField(metadata, fieldName, value);
                    fieldCount++;

                    if (LOG.isDebugEnabled()) {
                        String displayValue = truncateForDisplay(value, 50);
                        LOG.debug("Parsed RIFF INFO field: {} ({}) = {}", subChunkId, fieldName, displayValue);
                    }
                }
            }

            // Nächste Position berechnen (mit Padding für gerade Byte-Grenze)
            currentPos += 8 + subChunkSize;
            if (subChunkSize % 2 != 0) {
                currentPos++; // Padding Byte
            }
        }

        LOG.debug("Successfully parsed RIFF INFO chunk with {} fields", fieldCount);
    }

    private String parseNullTerminatedString(byte[] data) {
        // Finde das erste Null-Byte
        int length = data.length;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == 0) {
                length = i;
                break;
            }
        }

        if (length == 0) {
            return "";
        }

        // Versuche verschiedene Encodings
        try {
            // Versuche UTF-8
            String utf8 = new String(data, 0, length, StandardCharsets.UTF_8);
            if (isValidString(utf8)) {
                return utf8.trim();
            }
        } catch (Exception e) {
            // Fallback zu ISO-8859-1
        }

        try {
            // Fallback zu ISO-8859-1 (Windows-1252 ähnlich)
            return new String(data, 0, length, StandardCharsets.ISO_8859_1).trim();
        } catch (Exception e) {
            // Als letzter Ausweg: US-ASCII
            return new String(data, 0, length, StandardCharsets.US_ASCII).trim();
        }
    }

    private boolean isValidString(String str) {
        // Einfache Heuristik: String sollte druckbare Zeichen enthalten
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c < 32 && c != 9 && c != 10 && c != 13) { // Kontrollzeichen außer Tab, LF, CR
                return false;
            }
        }
        return true;
    }

}