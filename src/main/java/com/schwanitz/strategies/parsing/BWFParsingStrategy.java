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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BWFParsingStrategy implements TagParsingStrategy {
    private static final Logger Log = LoggerFactory.getLogger(BWFParsingStrategy.class);

    // BWF Chunk Struktur
    private static final int BWF_DESCRIPTION_SIZE = 256;
    private static final int BWF_ORIGINATOR_SIZE = 32;
    private static final int BWF_ORIGINATOR_REF_SIZE = 32;
    private static final int BWF_DATE_SIZE = 10;
    private static final int BWF_TIME_SIZE = 8;
    private static final int BWF_UMID_SIZE = 64;
    private static final int BWF_LOUDNESS_SIZE = 180; // For version 2

    // BWF Extension Chunk IDs
    private static final String BEXT_CUE_LIST = "cue ";
    private static final String BEXT_PEAK_ENVELOPE = "levl";
    private static final String BEXT_IXML = "iXML";
    private static final String BEXT_AXML = "axml";
    private static final String BEXT_LINK = "link";

    private final Map<String, FieldHandler<?>> handlers;

    public BWFParsingStrategy() {
        this.handlers = new HashMap<>();
        initializeDefaultHandlers();
    }

    private void initializeDefaultHandlers() {
        // BWF Standard Felder
        handlers.put("Description", new TextFieldHandler("Description"));
        handlers.put("Originator", new TextFieldHandler("Originator"));
        handlers.put("OriginatorReference", new TextFieldHandler("OriginatorReference"));
        handlers.put("OriginationDate", new TextFieldHandler("OriginationDate"));
        handlers.put("OriginationTime", new TextFieldHandler("OriginationTime"));
        handlers.put("OriginationDateTime", new TextFieldHandler("OriginationDateTime")); // NEU: Kombiniert
        handlers.put("TimeReference", new TextFieldHandler("TimeReference"));
        handlers.put("TimeReferenceTimecode", new TextFieldHandler("TimeReferenceTimecode")); // NEU: Als Timecode
        handlers.put("Version", new TextFieldHandler("Version"));
        handlers.put("UMID", new TextFieldHandler("UMID"));
        handlers.put("UMIDStructured", new TextFieldHandler("UMIDStructured")); // NEU: Strukturiert
        handlers.put("LoudnessValue", new TextFieldHandler("LoudnessValue"));
        handlers.put("LoudnessRange", new TextFieldHandler("LoudnessRange"));
        handlers.put("MaxTruePeakLevel", new TextFieldHandler("MaxTruePeakLevel"));
        handlers.put("MaxMomentaryLoudness", new TextFieldHandler("MaxMomentaryLoudness"));
        handlers.put("MaxShortTermLoudness", new TextFieldHandler("MaxShortTermLoudness"));
        handlers.put("CodingHistory", new TextFieldHandler("CodingHistory"));
        handlers.put("CodingHistoryStructured", new TextFieldHandler("CodingHistoryStructured")); // NEU: Strukturiert

        // BWF Extensions (NEU)
        handlers.put("CuePoints", new TextFieldHandler("CuePoints"));
        handlers.put("PeakEnvelope", new TextFieldHandler("PeakEnvelope"));
        handlers.put("iXMLMetadata", new TextFieldHandler("iXMLMetadata"));
        handlers.put("AdobeXMLMetadata", new TextFieldHandler("AdobeXMLMetadata"));
        handlers.put("LinkedFiles", new TextFieldHandler("LinkedFiles"));
    }

    @Override
    public boolean canHandle(TagFormat format) {
        return format == TagFormat.BWF_V0 || format == TagFormat.BWF_V1 || format == TagFormat.BWF_V2;
    }

    @Override
    public Metadata parseTag(TagFormat format, RandomAccessFile file, long offset, long size) throws IOException {
        BWFMetadata metadata = new BWFMetadata(format);
        parseBWFChunk(file, metadata, offset, size, format);
        return metadata;
    }

    private void parseBWFChunk(RandomAccessFile file, BWFMetadata metadata, long offset, long size, TagFormat format)
            throws IOException {
        file.seek(offset);

        // bext Chunk Header lesen
        byte[] chunkHeader = new byte[8];
        file.read(chunkHeader);

        String chunkId = new String(chunkHeader, 0, 4, StandardCharsets.US_ASCII);
        if (!"bext".equals(chunkId)) {
            throw new IOException("Expected bext chunk, found: " + chunkId);
        }

        int chunkSize = readLittleEndianInt32(chunkHeader, 4);
        Log.debug("Parsing BWF bext chunk with size: " + chunkSize);

        // Mindestgröße prüfen (602 Bytes für Version 0)
        if (chunkSize < 602) {
            throw new IOException("BWF chunk too small: " + chunkSize + " bytes");
        }

        // Description (256 bytes, null-padded)
        String description = readFixedString(file, BWF_DESCRIPTION_SIZE);
        if (!description.isEmpty()) {
            addField(metadata, "Description", description);
        }

        // Originator (32 bytes, null-padded)
        String originator = readFixedString(file, BWF_ORIGINATOR_SIZE);
        if (!originator.isEmpty()) {
            addField(metadata, "Originator", originator);
        }

        // Originator Reference (32 bytes, null-padded)
        String originatorRef = readFixedString(file, BWF_ORIGINATOR_REF_SIZE);
        if (!originatorRef.isEmpty()) {
            addField(metadata, "OriginatorReference", originatorRef);
        }

        // Origination Date (10 bytes, YYYY-MM-DD format)
        String originationDate = readFixedString(file, BWF_DATE_SIZE);
        if (!originationDate.isEmpty() && isValidDate(originationDate)) {
            addField(metadata, "OriginationDate", originationDate);
        }

        // Origination Time (8 bytes, HH:MM:SS format)
        String originationTime = readFixedString(file, BWF_TIME_SIZE);
        if (!originationTime.isEmpty() && isValidTime(originationTime)) {
            addField(metadata, "OriginationTime", originationTime);
        }

        // Kombiniertes DateTime-Feld erstellen (NEU)
        if (!originationDate.isEmpty() && !originationTime.isEmpty()) {
            String combinedDateTime = combineDateTime(originationDate, originationTime);
            if (!combinedDateTime.isEmpty()) {
                addField(metadata, "OriginationDateTime", combinedDateTime);
            }
        }

        // Time Reference (8 bytes, little-endian 64-bit)
        long timeReference = readLittleEndianInt64(file);
        addField(metadata, "TimeReference", String.valueOf(timeReference));

        // Time Reference als Timecode (NEU)
        String timecode = convertTimeReferenceToTimecode(timeReference, 48000); // Assume 48kHz default
        addField(metadata, "TimeReferenceTimecode", timecode);

        // Version (2 bytes, little-endian 16-bit)
        int version = readLittleEndianInt16(file);
        addField(metadata, "Version", String.valueOf(version));

        // UMID (64 bytes)
        byte[] umidData = new byte[BWF_UMID_SIZE];
        file.read(umidData);

        // Standard UMID Hex-String
        String umid = parseUMID(umidData);
        if (!umid.isEmpty()) {
            addField(metadata, "UMID", umid);
        }

        // Strukturierte UMID-Analyse (NEU)
        String structuredUMID = parseStructuredUMID(umidData);
        if (!structuredUMID.isEmpty()) {
            addField(metadata, "UMIDStructured", structuredUMID);
        }

        // Version-spezifische Felder
        if (format == TagFormat.BWF_V2 && chunkSize >= 602 + BWF_LOUDNESS_SIZE) {
            // Loudness Info (180 bytes für Version 2)
            parseLoudnessInfo(file, metadata);
        } else {
            // Reserved (190 bytes für Version 0/1)
            file.skipBytes(190);
        }

        // Coding History (variable length)
        int codingHistorySize = chunkSize - 602;
        if (format == TagFormat.BWF_V2) {
            codingHistorySize -= BWF_LOUDNESS_SIZE;
        }

        if (codingHistorySize > 0) {
            String codingHistory = readCodingHistory(file, codingHistorySize);
            if (!codingHistory.isEmpty()) {
                addField(metadata, "CodingHistory", codingHistory);

                // Strukturierte Coding History Analyse (NEU)
                String structuredHistory = parseStructuredCodingHistory(codingHistory);
                if (!structuredHistory.isEmpty()) {
                    addField(metadata, "CodingHistoryStructured", structuredHistory);
                }
            }
        }

        // BWF Extensions parsen (NEU)
        long currentPos = file.getFilePointer();
        if (currentPos < offset + size) {
            parseBWFExtensions(file, metadata, currentPos, offset + size - currentPos);
        }

        Log.debug("Successfully parsed BWF " + format.getFormatName() + " chunk");
    }

    private void parseLoudnessInfo(RandomAccessFile file, BWFMetadata metadata) throws IOException {
        // Loudness Value (2 bytes, signed little-endian, in LUFS * 100)
        int loudnessValue = readLittleEndianInt16(file);
        if (loudnessValue != -32768) { // -32768 = undefined
            addField(metadata, "LoudnessValue", String.format("%.2f LUFS", loudnessValue / 100.0));
        }

        // Loudness Range (2 bytes, unsigned little-endian, in LU * 100)
        int loudnessRange = readLittleEndianUInt16(file);
        if (loudnessRange != 0) {
            addField(metadata, "LoudnessRange", String.format("%.2f LU", loudnessRange / 100.0));
        }

        // Max True Peak Level (2 bytes, signed little-endian, in dBTP * 100)
        int maxTruePeak = readLittleEndianInt16(file);
        if (maxTruePeak != -32768) { // -32768 = undefined
            addField(metadata, "MaxTruePeakLevel", String.format("%.2f dBTP", maxTruePeak / 100.0));
        }

        // Max Momentary Loudness (2 bytes, signed little-endian, in LUFS * 100)
        int maxMomentary = readLittleEndianInt16(file);
        if (maxMomentary != -32768) {
            addField(metadata, "MaxMomentaryLoudness", String.format("%.2f LUFS", maxMomentary / 100.0));
        }

        // Max Short-term Loudness (2 bytes, signed little-endian, in LUFS * 100)
        int maxShortTerm = readLittleEndianInt16(file);
        if (maxShortTerm != -32768) {
            addField(metadata, "MaxShortTermLoudness", String.format("%.2f LUFS", maxShortTerm / 100.0));
        }

        // Reserved (170 bytes)
        file.skipBytes(170);
    }

    private void parseBWFExtensions(RandomAccessFile file, BWFMetadata metadata, long offset, long size)
            throws IOException {
        long currentPos = offset;
        long endPos = offset + size;

        Log.debug("Parsing BWF extensions from position " + currentPos + " to " + endPos);

        while (currentPos + 8 < endPos) {
            try {
                file.seek(currentPos);

                // Read chunk header (8 bytes)
                byte[] chunkHeader = new byte[8];
                int bytesRead = file.read(chunkHeader);
                if (bytesRead != 8) {
                    break;
                }

                String chunkId = new String(chunkHeader, 0, 4, StandardCharsets.US_ASCII);
                int chunkSize = readLittleEndianInt32(chunkHeader, 4);

                if (chunkSize < 0 || chunkSize > endPos - currentPos - 8) {
                    Log.debug("Invalid chunk size for " + chunkId + ": " + chunkSize);
                    break;
                }

                // Parse chunk based on type
                switch (chunkId) {
                    case BEXT_CUE_LIST:
                        parseCueListChunk(file, metadata, chunkSize);
                        break;

                    case BEXT_PEAK_ENVELOPE:
                        parsePeakEnvelopeChunk(file, metadata, chunkSize);
                        break;

                    case BEXT_IXML:
                        parseIXMLChunk(file, metadata, chunkSize);
                        break;

                    case BEXT_AXML:
                        parseAdobeXMLChunk(file, metadata, chunkSize);
                        break;

                    case BEXT_LINK:
                        parseLinkChunk(file, metadata, chunkSize);
                        break;

                    default:
                        Log.debug("Unknown BWF extension chunk: " + chunkId);
                        file.skipBytes(chunkSize);
                        break;
                }

                currentPos += 8 + chunkSize;

                // Padding für gerade Byte-Grenze
                if (chunkSize % 2 != 0 && currentPos < endPos) {
                    file.skipBytes(1);
                    currentPos++;
                }

            } catch (Exception e) {
                Log.warn("Error parsing BWF extension at position " + currentPos + ": " + e.getMessage());
                break;
            }
        }
    }

    private void parseCueListChunk(RandomAccessFile file, BWFMetadata metadata, int chunkSize) throws IOException {
        if (chunkSize < 4) {
            return;
        }

        // Number of cue points (4 bytes)
        int numCues = readLittleEndianInt32(file);

        StringBuilder cueInfo = new StringBuilder();
        cueInfo.append("Cue Points: ").append(numCues);

        int bytesRead = 4;
        for (int i = 0; i < numCues && bytesRead + 24 <= chunkSize; i++) {
            // Cue Point: ID(4) + Position(4) + FccChunk(4) + ChunkStart(4) + BlockStart(4) + SampleOffset(4)
            int cueId = readLittleEndianInt32(file);
            int position = readLittleEndianInt32(file);

            // Skip FccChunk, ChunkStart, BlockStart
            file.skipBytes(12);

            int sampleOffset = readLittleEndianInt32(file);
            bytesRead += 24;

            cueInfo.append("; Cue").append(i + 1).append(":ID=").append(cueId)
                    .append(",Pos=").append(position).append(",Offset=").append(sampleOffset);
        }

        addField(metadata, "CuePoints", cueInfo.toString());
        Log.debug("Parsed BWF Cue List with " + numCues + " cue points");
    }

    private void parsePeakEnvelopeChunk(RandomAccessFile file, BWFMetadata metadata, int chunkSize) throws IOException {
        if (chunkSize < 20) {
            return;
        }

        // Peak Envelope Header
        int version = readLittleEndianInt32(file);
        int format = readLittleEndianInt32(file);
        int pointsPerValue = readLittleEndianInt32(file);
        int blockSize = readLittleEndianInt32(file);
        int channels = readLittleEndianInt32(file);

        int numFrames = (chunkSize - 20) / (channels * 4); // Assuming 32-bit values

        String peakInfo = String.format("Peak Envelope: Version=%d, Format=%d, PointsPerValue=%d, " +
                        "BlockSize=%d, Channels=%d, Frames=%d",
                version, format, pointsPerValue, blockSize, channels, numFrames);

        addField(metadata, "PeakEnvelope", peakInfo);

        // Skip actual peak data
        file.skipBytes(chunkSize - 20);

        Log.debug("Parsed BWF Peak Envelope: " + channels + " channels, " + numFrames + " frames");
    }

    private void parseIXMLChunk(RandomAccessFile file, BWFMetadata metadata, int chunkSize) throws IOException {
        if (chunkSize <= 0) {
            return;
        }

        // iXML data is UTF-8 XML
        byte[] xmlData = new byte[chunkSize];
        file.read(xmlData);

        String xmlContent = new String(xmlData, StandardCharsets.UTF_8).trim();

        // Basic XML validation
        if (xmlContent.startsWith("<?xml") || xmlContent.startsWith("<BWFXML>")) {
            // Extract key metadata from iXML
            String extractedInfo = extractIXMLInfo(xmlContent);
            addField(metadata, "iXMLMetadata", extractedInfo);
            Log.debug("Parsed iXML metadata: " + extractedInfo.length() + " characters");
        } else {
            addField(metadata, "iXMLMetadata", "[iXML:" + chunkSize + " bytes]");
        }
    }

    private void parseAdobeXMLChunk(RandomAccessFile file, BWFMetadata metadata, int chunkSize) throws IOException {
        if (chunkSize <= 0) {
            return;
        }

        // Adobe XML data
        byte[] xmlData = new byte[chunkSize];
        file.read(xmlData);

        String xmlContent = new String(xmlData, StandardCharsets.UTF_8).trim();

        if (xmlContent.contains("adobe") || xmlContent.contains("xmp")) {
            String extractedInfo = extractAdobeXMLInfo(xmlContent);
            addField(metadata, "AdobeXMLMetadata", extractedInfo);
            Log.debug("Parsed Adobe XML metadata: " + extractedInfo.length() + " characters");
        } else {
            addField(metadata, "AdobeXMLMetadata", "[Adobe XML:" + chunkSize + " bytes]");
        }
    }

    private void parseLinkChunk(RandomAccessFile file, BWFMetadata metadata, int chunkSize) throws IOException {
        if (chunkSize <= 0) {
            return;
        }

        // Link chunk contains references to other BWF files
        byte[] linkData = new byte[chunkSize];
        file.read(linkData);

        String linkContent = new String(linkData, StandardCharsets.UTF_8).trim();

        // Extract linked file information
        String linkInfo = parseLinkContent(linkContent);
        addField(metadata, "LinkedFiles", linkInfo);

        Log.debug("Parsed BWF Link chunk: " + linkInfo);
    }

    private String combineDateTime(String date, String time) {
        try {
            LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            LocalTime localTime = LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm:ss"));
            LocalDateTime dateTime = LocalDateTime.of(localDate, localTime);

            return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException e) {
            Log.debug("Could not combine date/time: " + e.getMessage());
            return date + " " + time;
        }
    }

    private String convertTimeReferenceToTimecode(long timeReference, int sampleRate) {
        if (timeReference <= 0 || sampleRate <= 0) {
            return "00:00:00:00";
        }

        // Convert samples to timecode (assuming 25fps)
        double totalSeconds = (double) timeReference / sampleRate;

        int hours = (int) (totalSeconds / 3600);
        int minutes = (int) ((totalSeconds % 3600) / 60);
        int seconds = (int) (totalSeconds % 60);
        int frames = (int) ((totalSeconds % 1) * 25); // 25fps

        return String.format("%02d:%02d:%02d:%02d", hours, minutes, seconds, frames);
    }

    private String parseStructuredUMID(byte[] umidData) {
        if (umidData == null || umidData.length != 64) {
            return "";
        }

        // Check if UMID has content
        boolean hasContent = false;
        for (byte b : umidData) {
            if (b != 0) {
                hasContent = true;
                break;
            }
        }

        if (!hasContent) {
            return "";
        }

        try {
            // Basic UMID structure parsing according to SMPTE 330M
            int length = umidData[0] & 0xFF;
            int instance = ((umidData[1] & 0xFF) << 16) | ((umidData[2] & 0xFF) << 8) | (umidData[3] & 0xFF);

            // Material Number (16 bytes starting at offset 4)
            StringBuilder materialNum = new StringBuilder();
            for (int i = 4; i < 20; i++) {
                materialNum.append(String.format("%02X", umidData[i] & 0xFF));
            }

            // Time/Date stamp (8 bytes starting at offset 20)
            StringBuilder timestamp = new StringBuilder();
            for (int i = 20; i < 28; i++) {
                timestamp.append(String.format("%02X", umidData[i] & 0xFF));
            }

            return String.format("Length:%d, Instance:%d, Material:%s, Time:%s",
                    length, instance, materialNum.toString(), timestamp.toString());

        } catch (Exception e) {
            Log.debug("Error parsing UMID structure: " + e.getMessage());
            return ""; // Fallback to empty if parsing fails
        }
    }

    private String parseStructuredCodingHistory(String codingHistory) {
        if (codingHistory == null || codingHistory.isEmpty()) {
            return "";
        }

        // Coding History format: "A=PCM,F=48000,W=24,M=stereo,T=Original Recording"
        List<String> steps = new ArrayList<>();

        // Split by common delimiters (newline, semicolon, or specific patterns)
        String[] lines = codingHistory.split("[\r\n]+");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // Parse individual coding step
            Map<String, String> parameters = new HashMap<>();

            // Split by comma and parse key=value pairs
            String[] parts = line.split(",");
            for (String part : parts) {
                part = part.trim();
                if (part.contains("=")) {
                    String[] keyValue = part.split("=", 2);
                    if (keyValue.length == 2) {
                        parameters.put(keyValue[0].trim(), keyValue[1].trim());
                    }
                }
            }

            if (!parameters.isEmpty()) {
                StringBuilder stepInfo = new StringBuilder();
                stepInfo.append("Step: ");

                // Common coding history parameters
                if (parameters.containsKey("A")) stepInfo.append("Algorithm=").append(parameters.get("A")).append(" ");
                if (parameters.containsKey("F")) stepInfo.append("SampleRate=").append(parameters.get("F")).append("Hz ");
                if (parameters.containsKey("W")) stepInfo.append("WordLength=").append(parameters.get("W")).append("bit ");
                if (parameters.containsKey("M")) stepInfo.append("Mode=").append(parameters.get("M")).append(" ");
                if (parameters.containsKey("T")) stepInfo.append("Text=\"").append(parameters.get("T")).append("\"");

                steps.add(stepInfo.toString().trim());
            } else if (!line.isEmpty()) {
                // If no structured format detected, keep as-is
                steps.add("Step: " + line);
            }
        }

        return String.join("; ", steps);
    }

    private String extractIXMLInfo(String xmlContent) {
        // Extract key information from iXML
        StringBuilder info = new StringBuilder();
        info.append("iXML: ");

        // Basic pattern matching for common iXML fields
        String[] patterns = {
                "PROJECT=\"([^\"]+)\"", "SCENE=\"([^\"]+)\"", "TAPE=\"([^\"]+)\"",
                "TAKE=\"([^\"]+)\"", "NOTE=\"([^\"]+)\"", "SOUND_ROLL=\"([^\"]+)\""
        };

        String[] fieldNames = {"Project", "Scene", "Tape", "Take", "Note", "SoundRoll"};

        for (int i = 0; i < patterns.length; i++) {
            Pattern pattern = Pattern.compile(patterns[i]);
            Matcher matcher = pattern.matcher(xmlContent);
            if (matcher.find()) {
                if (info.length() > 6) info.append(", ");
                info.append(fieldNames[i]).append("=").append(matcher.group(1));
            }
        }

        if (info.length() == 6) { // Only "iXML: "
            info.append("Present (").append(xmlContent.length()).append(" characters)");
        }

        return info.toString();
    }

    private String extractAdobeXMLInfo(String xmlContent) {
        // Extract key information from Adobe XMP metadata
        StringBuilder info = new StringBuilder();
        info.append("Adobe XMP: ");

        // Basic pattern matching for common XMP fields
        if (xmlContent.contains("dc:title")) {
            info.append("Title present, ");
        }
        if (xmlContent.contains("dc:creator")) {
            info.append("Creator present, ");
        }
        if (xmlContent.contains("xmp:CreateDate")) {
            info.append("CreateDate present, ");
        }

        // Remove trailing comma
        String result = info.toString();
        if (result.endsWith(", ")) {
            result = result.substring(0, result.length() - 2);
        }

        if (result.equals("Adobe XMP: ")) {
            result = "Adobe XMP: Present (" + xmlContent.length() + " characters)";
        }

        return result;
    }

    private String parseLinkContent(String linkContent) {
        // Parse link information
        if (linkContent.isEmpty()) {
            return "No linked files";
        }

        // Basic parsing for file references
        String[] lines = linkContent.split("[\r\n]+");
        List<String> files = new ArrayList<>();

        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && (line.contains(".wav") || line.contains(".bwf") || line.contains("/"))) {
                files.add(line);
            }
        }

        if (files.isEmpty()) {
            return "Link data: " + linkContent.length() + " characters";
        }

        return "Linked files: " + String.join("; ", files);
    }

    private String readFixedString(RandomAccessFile file, int size) throws IOException {
        byte[] data = new byte[size];
        file.read(data);

        // Finde das erste Null-Byte oder verwende die ganze Länge
        int length = size;
        for (int i = 0; i < size; i++) {
            if (data[i] == 0) {
                length = i;
                break;
            }
        }

        if (length == 0) {
            return "";
        }

        return new String(data, 0, length, StandardCharsets.UTF_8).trim();
    }

    private String parseUMID(byte[] umidData) {
        // Prüfe ob UMID gesetzt ist (nicht alle Nullen)
        boolean hasContent = false;
        for (byte b : umidData) {
            if (b != 0) {
                hasContent = true;
                break;
            }
        }

        if (!hasContent) {
            return "";
        }

        // UMID als Hex-String formatieren
        StringBuilder sb = new StringBuilder();
        for (byte b : umidData) {
            sb.append(String.format("%02X", b & 0xFF));
        }

        return sb.toString();
    }

    private String readCodingHistory(RandomAccessFile file, int size) throws IOException {
        if (size <= 0) {
            return "";
        }

        byte[] data = new byte[size];
        file.read(data);

        // Coding History ist UTF-8 Text
        String history = new String(data, StandardCharsets.UTF_8).trim();

        // Entferne trailing nulls
        while (history.endsWith("\0")) {
            history = history.substring(0, history.length() - 1);
        }

        return history;
    }

    private boolean isValidDate(String date) {
        // YYYY-MM-DD Format prüfen
        return date.matches("\\d{4}-\\d{2}-\\d{2}");
    }

    private boolean isValidTime(String time) {
        // HH:MM:SS Format prüfen
        return time.matches("\\d{2}:\\d{2}:\\d{2}");
    }

    private int readLittleEndianInt16(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[2];
        file.read(bytes);
        return ((bytes[0] & 0xFF)) | ((bytes[1] & 0xFF) << 8);
    }

    private int readLittleEndianUInt16(RandomAccessFile file) throws IOException {
        return readLittleEndianInt16(file) & 0xFFFF;
    }

    private int readLittleEndianInt32(byte[] data, int offset) {
        return ((data[offset] & 0xFF)) |
                ((data[offset + 1] & 0xFF) << 8) |
                ((data[offset + 2] & 0xFF) << 16) |
                ((data[offset + 3] & 0xFF) << 24);
    }

    private int readLittleEndianInt32(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[4];
        file.read(bytes);
        return readLittleEndianInt32(bytes, 0);
    }

    private long readLittleEndianInt64(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[8];
        file.read(bytes);

        return ((long)(bytes[0] & 0xFF)) |
                ((long)(bytes[1] & 0xFF) << 8) |
                ((long)(bytes[2] & 0xFF) << 16) |
                ((long)(bytes[3] & 0xFF) << 24) |
                ((long)(bytes[4] & 0xFF) << 32) |
                ((long)(bytes[5] & 0xFF) << 40) |
                ((long)(bytes[6] & 0xFF) << 48) |
                ((long)(bytes[7] & 0xFF) << 56);
    }

    @SuppressWarnings("unchecked")
    private void addField(BWFMetadata metadata, String key, String value) {
        FieldHandler<?> handler = handlers.get(key);
        if (handler != null) {
            metadata.addField(new MetadataField<>(key, value, (FieldHandler<String>) handler));
        } else {
            TextFieldHandler textHandler = new TextFieldHandler(key);
            metadata.addField(new MetadataField<>(key, value, textHandler));
            Log.debug("Created fallback handler for unknown BWF field: " + key);
        }
    }

    public void registerHandler(String key, FieldHandler<?> handler) {
        handlers.put(key, handler);
    }

    // Innere Klasse für BWF Metadata
    public static class BWFMetadata implements Metadata {
        private final List<MetadataField<?>> fields = new ArrayList<>();
        private final TagFormat format;

        public BWFMetadata(TagFormat format) {
            this.format = format;
        }

        @Override
        public String getTagFormat() {
            return format.getFormatName();
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