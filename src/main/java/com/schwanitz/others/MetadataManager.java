package com.schwanitz.others;

import com.schwanitz.interfaces.FieldHandler;
import com.schwanitz.interfaces.Metadata;
import com.schwanitz.strategies.parsing.context.TagParsingContext;
import com.schwanitz.tagging.TagFormatDetector;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetadataManager {
    private final List<Metadata> metadataList = new ArrayList<>();
    private final Map<String, FieldHandler<?>> handlers = new HashMap<>();
    private final TagParsingContext parsingContext = new TagParsingContext();

    public MetadataManager() {
        // Standard-Handler registrieren
        registerHandler(new TextFieldHandler("TIT2")); // Titel (ID3)
        registerHandler(new TextFieldHandler("TPE1")); // Künstler (ID3)
        registerHandler(new TextFieldHandler("TALB")); // Album (ID3)
        registerHandler(new TextFieldHandler("TITLE")); // Titel (Vorbis)
        registerHandler(new TextFieldHandler("ARTIST")); // Künstler (Vorbis)
        registerHandler(new TextFieldHandler("ALBUM")); // Album (Vorbis)
        registerHandler(new TextFieldHandler("LYR")); // Songtext (Lyrics3)
    }

    public void registerHandler(FieldHandler<?> handler) {
        handlers.put(handler.getKey(), handler);
    }

    public void readFromFile(String filePath) throws IOException {
        // Tag-Formate mit TagFormatDetector erkennen
        List<TagInfo> detectedTags = TagFormatDetector.detectTagFormats(filePath, false);

        try (RandomAccessFile file = new RandomAccessFile(filePath, "r")) {
            for (TagInfo tagInfo : detectedTags) {
                try {
                    // Strategy Pattern verwenden statt switch-case
                    Metadata metadata = parsingContext.parseTag(
                            tagInfo.getFormat(),
                            file,
                            tagInfo.getOffset(),
                            tagInfo.getSize()
                    );
                    addMetadata(metadata);
                } catch (UnsupportedOperationException e) {
                    System.err.println("Nicht unterstütztes Tag-Format: " + tagInfo.getFormat());
                }
            }
        }
    }

    public void writeToFile(String filePath) throws IOException {
        // Platzhalter: Logik zum Schreiben der Metadaten im richtigen Tag-Format
    }

    public Metadata getMetadata(String tagFormat) {
        for (Metadata metadata : metadataList) {
            if (metadata.getTagFormat().equals(tagFormat)) {
                return metadata;
            }
        }
        return null;
    }

    public void addMetadata(Metadata metadata) {
        metadataList.add(metadata);
    }
}