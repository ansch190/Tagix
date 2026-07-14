package com.schwanitz.strategies.writing.unit;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.strategies.parsing.factory.TagParsingStrategyFactory;
import com.schwanitz.strategies.writing.DSFWritingStrategy;
import com.schwanitz.tagging.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DSFWritingStrategy Unit Tests")
class DSFWritingStrategyTest {

    private DSFWritingStrategy strategy;
    private TagParsingStrategyFactory parsingFactory;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parsingFactory = new TagParsingStrategyFactory();
        strategy = new DSFWritingStrategy(parsingFactory);
    }

    @Test
    @DisplayName("getSupportedWriteFormats enthält DSF_METADATA")
    void supportedFormats() {
        assertTrue(strategy.getSupportedWriteFormats().contains(TagFormat.DSF_METADATA));
    }

    @Test
    @DisplayName("supportsInPlaceWrite ist false")
    void inPlace() {
        assertFalse(strategy.supportsInPlaceWrite(TagFormat.DSF_METADATA));
    }

    @Test
    @DisplayName("writeTag delegiert an ID3WritingStrategy")
    void writeDSF() throws IOException {
        byte[] audioData = new byte[100];
        audioData[0] = 'D'; audioData[1] = 'S'; audioData[2] = 'D'; audioData[3] = ' ';
        Path file = tempDir.resolve("test.dsf");
        Files.write(file, audioData);

        GenericMetadata meta = new GenericMetadata(TagFormat.DSF_METADATA);
        meta.addField(new MetadataField<>("TIT2", "DSF Title", new TextFieldHandler("TIT2")));
        meta.addField(new MetadataField<>("TPE1", "DSF Artist", new TextFieldHandler("TPE1")));

        WriteConfiguration config = WriteConfiguration.defaults();

        try (SeekableDataSource source = com.schwanitz.io.SeekableDataSources.forPath(file)) {
            WriteResult result = strategy.writeTag(TagFormat.DSF_METADATA, meta, source, null, config);
            assertTrue(result.success());
        }
    }
}
