package com.schwanitz.strategies.writing.unit;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.strategies.parsing.factory.TagParsingStrategyFactory;
import com.schwanitz.strategies.writing.ASFWritingStrategy;
import com.schwanitz.tagging.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ASFWritingStrategy Unit Tests")
class ASFWritingStrategyTest {

    private ASFWritingStrategy strategy;
    private TagParsingStrategyFactory parsingFactory;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parsingFactory = new TagParsingStrategyFactory();
        strategy = new ASFWritingStrategy(parsingFactory);
    }

    @Test
    @DisplayName("getSupportedWriteFormats enthält ASF")
    void supportedFormats() {
        assertTrue(strategy.getSupportedWriteFormats().contains(TagFormat.ASF_CONTENT_DESC));
        assertTrue(strategy.getSupportedWriteFormats().contains(TagFormat.ASF_EXT_CONTENT_DESC));
    }

    @Test
    @DisplayName("supportsInPlaceWrite ist false")
    void inPlace() {
        assertFalse(strategy.supportsInPlaceWrite(TagFormat.ASF_CONTENT_DESC));
    }

    @Test
    @DisplayName("writeTag für ASF Content Description")
    void writeASF() throws IOException {
        byte[] audioData = new byte[100];
        Path file = tempDir.resolve("test.wma");
        Files.write(file, audioData);

        GenericMetadata meta = new GenericMetadata(TagFormat.ASF_CONTENT_DESC);
        meta.addField(new MetadataField<>("Title", "ASF Title", new TextFieldHandler("Title")));
        meta.addField(new MetadataField<>("Author", "ASF Author", new TextFieldHandler("Author")));
        meta.addField(new MetadataField<>("Copyright", "ASF Copyright", new TextFieldHandler("Copyright")));

        WriteConfiguration config = WriteConfiguration.defaults();

        try (SeekableDataSource source = com.schwanitz.io.SeekableDataSources.forPath(file)) {
            WriteResult result = strategy.writeTag(TagFormat.ASF_CONTENT_DESC, meta, source, null, config);
            assertTrue(result.success());
            assertEquals(TagFormat.ASF_CONTENT_DESC, result.format());
        }
    }
}
