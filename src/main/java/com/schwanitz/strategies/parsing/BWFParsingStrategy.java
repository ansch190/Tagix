package com.schwanitz.strategies.parsing;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.io.BinaryDataReader;
import com.schwanitz.strategies.parsing.bwf.BWFCodingHistoryParser;
import com.schwanitz.strategies.parsing.bwf.BWFExtensionParser;
import com.schwanitz.strategies.parsing.bwf.BWFTimeUtils;
import com.schwanitz.strategies.parsing.bwf.BWFUmidParser;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.tagging.TagFormat;

import java.io.IOException;
import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.io.SourceReader;

import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parsing-Strategie für BWF-Metadaten (Broadcast Wave Format, Version 0/1/2).
 *
 * <p>Das BWF-Format erweitert das Standard-WAV-Format um einen {@code bext}-Chunk, der professionelle
 * Audio-Metadaten wie Beschreibung, Urheber, Zeitreferenz (als Samples und als Timecode), UMID und
 * Loudness-Informationen enthält. Version 2 fügt zusätzlich Loudness-Metadaten (LUFS, dBTP) hinzu.
 * Diese Strategie liest den kompletten {@code bext}-Chunk und parst optional folgende Extensions:
 * Cue-List, Peak-Envelope, iXML und Adobe-XML-Metadaten.</p>
 *
 * <p>Unterstützte Formate:</p>
 * <ul>
 *   <li>{@link TagFormat#BWF_V0} – BWF Version 0 (Grundstruktur ohne Loudness)</li>
 *   <li>{@link TagFormat#BWF_V1} – BWF Version 1 (Grundstruktur ohne Loudness)</li>
 *   <li>{@link TagFormat#BWF_V2} – BWF Version 2 (inklusive Loudness-Informationen)</li>
 * </ul>
 *
 * @see TagParsingStrategy
 * @see BWFExtensionParser
 * @see BWFUmidParser
 * @see BWFCodingHistoryParser
 * @see BWFTimeUtils
 */
public class BWFParsingStrategy extends AbstractTagParsingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(BWFParsingStrategy.class);

    private static final int BWF_DESCRIPTION_SIZE = 256;
    private static final int BWF_ORIGINATOR_SIZE = 32;
    private static final int BWF_ORIGINATOR_REF_SIZE = 32;
    private static final int BWF_DATE_SIZE = 10;
    private static final int BWF_TIME_SIZE = 8;
    private static final int BWF_UMID_SIZE = 64;
    private static final int BWF_LOUDNESS_SIZE = 180;

    private static final int BWF_LOUDNESS_SENTINEL = -32768;
    private static final int BWF_DEFAULT_SAMPLE_RATE = 48000;


    private final BWFExtensionParser extensionParser;

    public BWFParsingStrategy() {
        super("BWF");
        initializeDefaultHandlers();
        this.extensionParser = new BWFExtensionParser(handlers);
    }

    private void initializeDefaultHandlers() {
        handlers.put("Description", new TextFieldHandler("Description"));
        handlers.put("Originator", new TextFieldHandler("Originator"));
        handlers.put("OriginatorReference", new TextFieldHandler("OriginatorReference"));
        handlers.put("OriginationDate", new TextFieldHandler("OriginationDate"));
        handlers.put("OriginationTime", new TextFieldHandler("OriginationTime"));
        handlers.put("OriginationDateTime", new TextFieldHandler("OriginationDateTime"));
        handlers.put("TimeReference", new TextFieldHandler("TimeReference"));
        handlers.put("TimeReferenceTimecode", new TextFieldHandler("TimeReferenceTimecode"));
        handlers.put("Version", new TextFieldHandler("Version"));
        handlers.put("UMID", new TextFieldHandler("UMID"));
        handlers.put("UMIDStructured", new TextFieldHandler("UMIDStructured"));
        handlers.put("LoudnessValue", new TextFieldHandler("LoudnessValue"));
        handlers.put("LoudnessRange", new TextFieldHandler("LoudnessRange"));
        handlers.put("MaxTruePeakLevel", new TextFieldHandler("MaxTruePeakLevel"));
        handlers.put("MaxMomentaryLoudness", new TextFieldHandler("MaxMomentaryLoudness"));
        handlers.put("MaxShortTermLoudness", new TextFieldHandler("MaxShortTermLoudness"));
        handlers.put("CodingHistory", new TextFieldHandler("CodingHistory"));
        handlers.put("CodingHistoryStructured", new TextFieldHandler("CodingHistoryStructured"));

        handlers.put("CuePoints", new TextFieldHandler("CuePoints"));
        handlers.put("PeakEnvelope", new TextFieldHandler("PeakEnvelope"));
        handlers.put("iXMLMetadata", new TextFieldHandler("iXMLMetadata"));
        handlers.put("AdobeXMLMetadata", new TextFieldHandler("AdobeXMLMetadata"));
        handlers.put("LinkedFiles", new TextFieldHandler("LinkedFiles"));
    }

    @Override
    public Metadata parseTag(TagFormat format, SeekableDataSource source, long offset, long size) throws IOException {
        GenericMetadata metadata = new GenericMetadata(format);
        SourceReader reader = new SourceReader(source, offset);
        parseBWFChunk(reader, metadata, offset, size, format);
        return metadata;
    }

    private void parseBWFChunk(SourceReader reader, GenericMetadata metadata, long offset, long size, TagFormat format)
            throws IOException {
        reader.seek(offset);

        byte[] chunkHeader = new byte[8];
        reader.readFully(chunkHeader);

        String chunkId = new String(chunkHeader, 0, 4, StandardCharsets.US_ASCII);
        if (!"bext".equals(chunkId)) {
            throw new IOException("Expected bext chunk, found: " + chunkId);
        }

        int chunkSize = BinaryDataReader.readLittleEndianInt32(chunkHeader, 4);
        LOG.debug("Parsing BWF bext chunk with size: {}", chunkSize);

        if (chunkSize < 602) {
            throw new IOException("BWF chunk too small: " + chunkSize + " bytes");
        }

        String description = BinaryDataReader.readFixedString(reader, BWF_DESCRIPTION_SIZE);
        if (!description.isEmpty()) {
            addField(metadata, "Description", description);
        }

        String originator = BinaryDataReader.readFixedString(reader, BWF_ORIGINATOR_SIZE);
        if (!originator.isEmpty()) {
            addField(metadata, "Originator", originator);
        }

        String originatorRef = BinaryDataReader.readFixedString(reader, BWF_ORIGINATOR_REF_SIZE);
        if (!originatorRef.isEmpty()) {
            addField(metadata, "OriginatorReference", originatorRef);
        }

        String originationDate = BinaryDataReader.readFixedString(reader, BWF_DATE_SIZE);
        if (!originationDate.isEmpty() && BWFTimeUtils.isValidDate(originationDate)) {
            addField(metadata, "OriginationDate", originationDate);
        }

        String originationTime = BinaryDataReader.readFixedString(reader, BWF_TIME_SIZE);
        if (!originationTime.isEmpty() && BWFTimeUtils.isValidTime(originationTime)) {
            addField(metadata, "OriginationTime", originationTime);
        }

        if (!originationDate.isEmpty() && !originationTime.isEmpty()) {
            String combinedDateTime = BWFTimeUtils.combineDateTime(originationDate, originationTime);
            if (!combinedDateTime.isEmpty()) {
                addField(metadata, "OriginationDateTime", combinedDateTime);
            }
        }

        long timeReference = BinaryDataReader.readLittleEndianInt64(reader);
        addField(metadata, "TimeReference", String.valueOf(timeReference));

        String timecode = BWFTimeUtils.convertTimeReferenceToTimecode(timeReference, BWF_DEFAULT_SAMPLE_RATE);
        addField(metadata, "TimeReferenceTimecode", timecode);

        int version = BinaryDataReader.readLittleEndianInt16(reader);
        addField(metadata, "Version", String.valueOf(version));

        byte[] umidData = new byte[BWF_UMID_SIZE];
        reader.readFully(umidData);

        String umid = BWFUmidParser.parseUMID(umidData);
        if (!umid.isEmpty()) {
            addField(metadata, "UMID", umid);
        }

        String structuredUMID = BWFUmidParser.parseStructuredUMID(umidData);
        if (!structuredUMID.isEmpty()) {
            addField(metadata, "UMIDStructured", structuredUMID);
        }

        if (format == TagFormat.BWF_V2 && chunkSize >= 602 + BWF_LOUDNESS_SIZE) {
            parseLoudnessInfo(reader, metadata);
        } else {
            reader.skipBytes(190);
        }

        int codingHistorySize = chunkSize - 602;
        if (format == TagFormat.BWF_V2) {
            codingHistorySize -= BWF_LOUDNESS_SIZE;
        }

        if (codingHistorySize > 0) {
            byte[] codingHistoryBytes = new byte[codingHistorySize];
            reader.readFully(codingHistoryBytes);
            String codingHistory = new String(codingHistoryBytes, StandardCharsets.UTF_8).trim();
            while (codingHistory.endsWith("\0")) {
                codingHistory = codingHistory.substring(0, codingHistory.length() - 1);
            }
            if (!codingHistory.isEmpty()) {
                addField(metadata, "CodingHistory", codingHistory);

                String structuredHistory = BWFCodingHistoryParser.parseStructuredCodingHistory(codingHistory);
                if (!structuredHistory.isEmpty()) {
                    addField(metadata, "CodingHistoryStructured", structuredHistory);
                }
            }
        }

        long currentPos = reader.getFilePointer();
        if (currentPos < offset + size) {
            extensionParser.parseBWFExtensions(reader, metadata, currentPos, offset + size - currentPos);
        }

        LOG.debug("Successfully parsed BWF {} chunk", format.getFormatName());
    }

    private void parseLoudnessInfo(SourceReader reader, GenericMetadata metadata) throws IOException {
        int loudnessValue = BinaryDataReader.readLittleEndianInt16(reader);
        if (loudnessValue != BWF_LOUDNESS_SENTINEL) {
            addField(metadata, "LoudnessValue", String.format("%.2f LUFS", loudnessValue / 100.0));
        }

        int loudnessRange = BinaryDataReader.readLittleEndianUInt16(reader);
        if (loudnessRange != 0) {
            addField(metadata, "LoudnessRange", String.format("%.2f LU", loudnessRange / 100.0));
        }

        int maxTruePeak = BinaryDataReader.readLittleEndianInt16(reader);
        if (maxTruePeak != BWF_LOUDNESS_SENTINEL) {
            addField(metadata, "MaxTruePeakLevel", String.format("%.2f dBTP", maxTruePeak / 100.0));
        }

        int maxMomentary = BinaryDataReader.readLittleEndianInt16(reader);
        if (maxMomentary != BWF_LOUDNESS_SENTINEL) {
            addField(metadata, "MaxMomentaryLoudness", String.format("%.2f LUFS", maxMomentary / 100.0));
        }

        int maxShortTerm = BinaryDataReader.readLittleEndianInt16(reader);
        if (maxShortTerm != BWF_LOUDNESS_SENTINEL) {
            addField(metadata, "MaxShortTermLoudness", String.format("%.2f LUFS", maxShortTerm / 100.0));
        }

        reader.skipBytes(170);
    }


}