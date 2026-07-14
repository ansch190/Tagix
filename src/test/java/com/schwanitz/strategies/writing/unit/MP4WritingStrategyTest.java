package com.schwanitz.strategies.writing.unit;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.strategies.parsing.factory.TagParsingStrategyFactory;
import com.schwanitz.strategies.writing.MP4WritingStrategy;
import com.schwanitz.tagging.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MP4WritingStrategy Unit Tests")
class MP4WritingStrategyTest {

    private MP4WritingStrategy strategy;
    private TagParsingStrategyFactory parsingFactory;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parsingFactory = new TagParsingStrategyFactory();
        strategy = new MP4WritingStrategy(parsingFactory);
    }

    @Test
    @DisplayName("getSupportedWriteFormats enthält MP4")
    void supportedFormats() {
        assertTrue(strategy.getSupportedWriteFormats().contains(TagFormat.MP4));
    }

    @Test
    @DisplayName("supportsInPlaceWrite ist false")
    void inPlace() {
        assertFalse(strategy.supportsInPlaceWrite(TagFormat.MP4));
    }

    @Test
    @DisplayName("writeTag für MP4 mit iTunes ilst")
    void writeMP4() throws IOException {
        byte[] audioData = new byte[100];
        Path file = tempDir.resolve("test.m4a");
        Files.write(file, audioData);

        GenericMetadata meta = new GenericMetadata(TagFormat.MP4);
        meta.addField(new MetadataField<>("©nam", "MP4 Title", new TextFieldHandler("©nam")));
        meta.addField(new MetadataField<>("©ART", "MP4 Artist", new TextFieldHandler("©ART")));
        meta.addField(new MetadataField<>("©alb", "MP4 Album", new TextFieldHandler("©alb")));

        WriteConfiguration config = WriteConfiguration.defaults();

        try (SeekableDataSource source = com.schwanitz.io.SeekableDataSources.forPath(file)) {
            WriteResult result = strategy.writeTag(TagFormat.MP4, meta, source, null, config);
            assertTrue(result.success());
            assertEquals(TagFormat.MP4, result.format());
        }
    }
}
