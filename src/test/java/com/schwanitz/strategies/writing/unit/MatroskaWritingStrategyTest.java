package com.schwanitz.strategies.writing.unit;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.strategies.parsing.factory.TagParsingStrategyFactory;
import com.schwanitz.strategies.writing.MatroskaWritingStrategy;
import com.schwanitz.tagging.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MatroskaWritingStrategy Unit Tests")
class MatroskaWritingStrategyTest {

    private MatroskaWritingStrategy strategy;
    private TagParsingStrategyFactory parsingFactory;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parsingFactory = new TagParsingStrategyFactory();
        strategy = new MatroskaWritingStrategy(parsingFactory);
    }

    @Test
    @DisplayName("getSupportedWriteFormats enthält MATROSKA_TAGS und WEBM_TAGS")
    void supportedFormats() {
        assertTrue(strategy.getSupportedWriteFormats().contains(TagFormat.MATROSKA_TAGS));
        assertTrue(strategy.getSupportedWriteFormats().contains(TagFormat.WEBM_TAGS));
    }

    @Test
    @DisplayName("supportsInPlaceWrite ist false")
    void inPlace() {
        assertFalse(strategy.supportsInPlaceWrite(TagFormat.MATROSKA_TAGS));
    }

    @Test
    @DisplayName("writeTag für Matroska")
    void writeMatroska() throws IOException {
        byte[] audioData = new byte[100];
        Path file = tempDir.resolve("test.mkv");
        Files.write(file, audioData);

        GenericMetadata meta = new GenericMetadata(TagFormat.MATROSKA_TAGS);
        meta.addField(new MetadataField<>("TITLE", "MKV Title", new TextFieldHandler("TITLE")));
        meta.addField(new MetadataField<>("ARTIST", "MKV Artist", new TextFieldHandler("ARTIST")));

        WriteConfiguration config = WriteConfiguration.defaults();

        try (SeekableDataSource source = com.schwanitz.io.SeekableDataSources.forPath(file)) {
            WriteResult result = strategy.writeTag(TagFormat.MATROSKA_TAGS, meta, source, null, config);
            assertTrue(result.success());
        }
    }

    @Test
    @DisplayName("writeTag für WebM")
    void writeWebM() throws IOException {
        byte[] audioData = new byte[100];
        Path file = tempDir.resolve("test.webm");
        Files.write(file, audioData);

        GenericMetadata meta = new GenericMetadata(TagFormat.WEBM_TAGS);
        meta.addField(new MetadataField<>("TITLE", "WebM Title", new TextFieldHandler("TITLE")));

        WriteConfiguration config = WriteConfiguration.defaults();

        try (SeekableDataSource source = com.schwanitz.io.SeekableDataSources.forPath(file)) {
            WriteResult result = strategy.writeTag(TagFormat.WEBM_TAGS, meta, source, null, config);
            assertTrue(result.success());
        }
    }
}
