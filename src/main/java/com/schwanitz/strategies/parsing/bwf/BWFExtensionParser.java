package com.schwanitz.strategies.parsing.bwf;

import com.schwanitz.io.BinaryDataReader;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.interfaces.FieldHandler;

import java.io.IOException;
import com.schwanitz.io.SourceReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BWFExtensionParser {

    private static final Logger LOG = LoggerFactory.getLogger(BWFExtensionParser.class);

    private static final String BEXT_CUE_LIST = "cue ";
    private static final String BEXT_PEAK_ENVELOPE = "levl";
    private static final String BEXT_IXML = "iXML";
    private static final String BEXT_AXML = "axml";
    private static final String BEXT_LINK = "link";

    private static final int MAX_BWF_EXTENSION_SIZE = 10_000_000; // 10 MB

    private static final Pattern[] IXML_PATTERNS = {
            Pattern.compile("PROJECT=\"([^\"]+)\""),
            Pattern.compile("SCENE=\"([^\"]+)\""),
            Pattern.compile("TAPE=\"([^\"]+)\""),
            Pattern.compile("TAKE=\"([^\"]+)\""),
            Pattern.compile("NOTE=\"([^\"]+)\""),
            Pattern.compile("SOUND_ROLL=\"([^\"]+)\""),
    };
    private static final String[] IXML_FIELD_NAMES = {"Project", "Scene", "Tape", "Take", "Note", "SoundRoll"};

    private final Map<String, FieldHandler<?>> handlers;

    public BWFExtensionParser(Map<String, FieldHandler<?>> handlers) {
        this.handlers = handlers;
    }

    public void parseBWFExtensions(SourceReader reader, GenericMetadata metadata, long offset, long size) {
        long currentPos = offset;
        long endPos = offset + size;

        LOG.debug("Parsing BWF extensions from position {} to {}", currentPos, endPos);

        while (currentPos + 8 < endPos) {
            try {
                reader.seek(currentPos);

                byte[] chunkHeader = new byte[8];
                reader.readFully(chunkHeader);

                String chunkId = new String(chunkHeader, 0, 4, StandardCharsets.US_ASCII);
                int chunkSize = BinaryDataReader.readLittleEndianInt32(chunkHeader, 4);

                if (chunkSize < 0 || chunkSize > endPos - currentPos - 8) {
                    LOG.debug("Invalid chunk size for {}: {}", chunkId, chunkSize);
                    break;
                }

                switch (chunkId) {
                    case BEXT_CUE_LIST:
                        parseCueListChunk(reader, metadata, chunkSize);
                        break;

                    case BEXT_PEAK_ENVELOPE:
                        parsePeakEnvelopeChunk(reader, metadata, chunkSize);
                        break;

                    case BEXT_IXML:
                        parseIXMLChunk(reader, metadata, chunkSize);
                        break;

                    case BEXT_AXML:
                        parseAdobeXMLChunk(reader, metadata, chunkSize);
                        break;

                    case BEXT_LINK:
                        parseLinkChunk(reader, metadata, chunkSize);
                        break;

                    default:
                        LOG.debug("Unknown BWF extension chunk: {}", chunkId);
                        reader.skipBytes(chunkSize);
                        break;
                }

                currentPos += 8 + chunkSize;

                if (chunkSize % 2 != 0 && currentPos < endPos) {
                    reader.skipBytes(1);
                    currentPos++;
                }

            } catch (Exception e) {
                LOG.warn("Error parsing BWF extension at position {}", currentPos, e);
                break;
            }
        }
    }

    private void parseCueListChunk(SourceReader reader, GenericMetadata metadata, int chunkSize) throws IOException {
        if (chunkSize < 4) {
            return;
        }

        int numCues = BinaryDataReader.readLittleEndianInt32(reader);

        StringBuilder cueInfo = new StringBuilder();
        cueInfo.append("Cue Points: ").append(numCues);

        int bytesRead = 4;
        for (int i = 0; i < numCues && bytesRead + 24 <= chunkSize; i++) {
            int cueId = BinaryDataReader.readLittleEndianInt32(reader);
            int position = BinaryDataReader.readLittleEndianInt32(reader);

            reader.skipBytes(12);

            int sampleOffset = BinaryDataReader.readLittleEndianInt32(reader);
            bytesRead += 24;

            cueInfo.append("; Cue").append(i + 1).append(":ID=").append(cueId)
                    .append(",Pos=").append(position).append(",Offset=").append(sampleOffset);
        }

        addField(metadata, "CuePoints", cueInfo.toString());
        LOG.debug("Parsed BWF Cue List with {} cue points", numCues);
    }

    private void parsePeakEnvelopeChunk(SourceReader reader, GenericMetadata metadata, int chunkSize) throws IOException {
        if (chunkSize < 20) {
            return;
        }

        int version = BinaryDataReader.readLittleEndianInt32(reader);
        int format = BinaryDataReader.readLittleEndianInt32(reader);
        int pointsPerValue = BinaryDataReader.readLittleEndianInt32(reader);
        int blockSize = BinaryDataReader.readLittleEndianInt32(reader);
        int channels = BinaryDataReader.readLittleEndianInt32(reader);

        int numFrames = (chunkSize - 20) / (channels * 4);

        String peakInfo = String.format("Peak Envelope: Version=%d, Format=%d, PointsPerValue=%d, " +
                        "BlockSize=%d, Channels=%d, Frames=%d",
                version, format, pointsPerValue, blockSize, channels, numFrames);

        addField(metadata, "PeakEnvelope", peakInfo);

        reader.skipBytes(chunkSize - 20);

        LOG.debug("Parsed BWF Peak Envelope: {} channels, {} frames", channels, numFrames);
    }

    private void parseIXMLChunk(SourceReader reader, GenericMetadata metadata, int chunkSize) throws IOException {
        if (chunkSize <= 0) {
            return;
        }
        if (chunkSize > MAX_BWF_EXTENSION_SIZE) {
            LOG.warn("BWF iXML chunk too large: {} bytes, skipping", chunkSize);
            reader.seek(reader.getFilePointer() + chunkSize);
            return;
        }

        byte[] xmlData = new byte[chunkSize];
        reader.readFully(xmlData);

        String xmlContent = new String(xmlData, StandardCharsets.UTF_8).trim();

        if (xmlContent.startsWith("<?xml") || xmlContent.startsWith("<BWFXML>")) {
            String extractedInfo = extractIXMLInfo(xmlContent);
            addField(metadata, "iXMLMetadata", extractedInfo);
            LOG.debug("Parsed iXML metadata: {} characters", extractedInfo.length());
        } else {
            addField(metadata, "iXMLMetadata", "[iXML:" + chunkSize + " bytes]");
        }
    }

    private void parseAdobeXMLChunk(SourceReader reader, GenericMetadata metadata, int chunkSize) throws IOException {
        if (chunkSize <= 0) {
            return;
        }
        if (chunkSize > MAX_BWF_EXTENSION_SIZE) {
            LOG.warn("BWF Adobe XML chunk too large: {} bytes, skipping", chunkSize);
            reader.seek(reader.getFilePointer() + chunkSize);
            return;
        }

        byte[] xmlData = new byte[chunkSize];
        reader.readFully(xmlData);

        String xmlContent = new String(xmlData, StandardCharsets.UTF_8).trim();

        if (xmlContent.contains("adobe") || xmlContent.contains("xmp")) {
            String extractedInfo = extractAdobeXMLInfo(xmlContent);
            addField(metadata, "AdobeXMLMetadata", extractedInfo);
            LOG.debug("Parsed Adobe XML metadata: {} characters", extractedInfo.length());
        } else {
            addField(metadata, "AdobeXMLMetadata", "[Adobe XML:" + chunkSize + " bytes]");
        }
    }

    private void parseLinkChunk(SourceReader reader, GenericMetadata metadata, int chunkSize) throws IOException {
        if (chunkSize <= 0) {
            return;
        }
        if (chunkSize > MAX_BWF_EXTENSION_SIZE) {
            LOG.warn("BWF Link chunk too large: {} bytes, skipping", chunkSize);
            reader.seek(reader.getFilePointer() + chunkSize);
            return;
        }

        byte[] linkData = new byte[chunkSize];
        reader.readFully(linkData);

        String linkContent = new String(linkData, StandardCharsets.UTF_8).trim();

        String linkInfo = parseLinkContent(linkContent);
        addField(metadata, "LinkedFiles", linkInfo);

        LOG.debug("Parsed BWF Link chunk: {}", linkInfo);
    }

    private String extractIXMLInfo(String xmlContent) {
        StringBuilder info = new StringBuilder();
        info.append("iXML: ");

        for (int i = 0; i < IXML_PATTERNS.length; i++) {
            Matcher matcher = IXML_PATTERNS[i].matcher(xmlContent);
            if (matcher.find()) {
                if (info.length() > 6) info.append(", ");
                info.append(IXML_FIELD_NAMES[i]).append("=").append(matcher.group(1));
            }
        }

        if (info.length() == 6) {
            info.append("Present (").append(xmlContent.length()).append(" characters)");
        }

        return info.toString();
    }

    private String extractAdobeXMLInfo(String xmlContent) {
        StringBuilder info = new StringBuilder();
        info.append("Adobe XMP: ");

        if (xmlContent.contains("dc:title")) {
            info.append("Title present, ");
        }
        if (xmlContent.contains("dc:creator")) {
            info.append("Creator present, ");
        }
        if (xmlContent.contains("xmp:CreateDate")) {
            info.append("CreateDate present, ");
        }

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
        if (linkContent.isEmpty()) {
            return "No linked files";
        }

        String[] lines = linkContent.split("[\r\n]+");
        StringBuilder files = new StringBuilder();

        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && (line.contains(".wav") || line.contains(".bwf") || line.contains("/"))) {
                if (!files.isEmpty()) files.append("; ");
                files.append(line);
            }
        }

        if (files.isEmpty()) {
            return "Link data: " + linkContent.length() + " characters";
        }

        return "Linked files: " + files;
    }

    @SuppressWarnings("unchecked")
    private void addField(GenericMetadata metadata, String key, String value) {
        FieldHandler<?> handler = handlers.get(key);
        if (handler != null) {
            metadata.addField(new MetadataField<>(key, value, (FieldHandler<String>) handler));
        } else {
            TextFieldHandler textHandler = new TextFieldHandler(key);
            metadata.addField(new MetadataField<>(key, value, textHandler));
            LOG.debug("Created fallback handler for unknown BWF extension field: {}", key);
        }
    }
}