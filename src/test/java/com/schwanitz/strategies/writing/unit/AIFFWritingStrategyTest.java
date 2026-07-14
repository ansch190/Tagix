package com.schwanitz.strategies.writing.unit;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.strategies.parsing.factory.TagParsingStrategyFactory;
import com.schwanitz.strategies.writing.AIFFWritingStrategy;
import com.schwanitz.tagging.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AIFFWritingStrategy Unit Tests")
class AIFFWritingStrategyTest {

    private AIFFWritingStrategy strategy;
    private TagParsingStrategyFactory parsingFactory;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parsingFactory = new TagParsingStrategyFactory();
        strategy = new AIFFWritingStrategy(parsingFactory);
    }

    @Test
    @DisplayName("getSupportedWriteFormats enthält AIFF_METADATA")
    void supportedFormats() {
        assertTrue(strategy.getSupportedWriteFormats().contains(TagFormat.AIFF_METADATA));
    }

    @Test
    @DisplayName("supportsInPlaceWrite ist false")
    void inPlace() {
        assertFalse(strategy.supportsInPlaceWrite(TagFormat.AIFF_METADATA));
    }

    @Test
    @DisplayName("writeTag für AIFF")
    void writeAIFF() throws IOException {
        byte[] audioData = new byte[100];
        Path file = tempDir.resolve("test.aiff");
        Files.write(file, audioData);

        GenericMetadata meta = new GenericMetadata(TagFormat.AIFF_METADATA);
        meta.addField(new MetadataField<>("NAME", "AIFF Title", new TextFieldHandler("NAME")));
        meta.addField(new MetadataField<>("AUTH", "AIFF Author", new TextFieldHandler("AUTH")));
        meta.addField(new MetadataField<>("ANNO", "AIFF Annotation", new TextFieldHandler("ANNO")));

        WriteConfiguration config = WriteConfiguration.defaults();

        try (SeekableDataSource source = com.schwanitz.io.SeekableDataSources.forPath(file)) {
            WriteResult result = strategy.writeTag(TagFormat.AIFF_METADATA, meta, source, null, config);
            assertTrue(result.success());
        }
    }
}
