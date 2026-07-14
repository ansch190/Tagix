package com.schwanitz.strategies.writing.unit;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.strategies.parsing.factory.TagParsingStrategyFactory;
import com.schwanitz.strategies.writing.Lyrics3WritingStrategy;
import com.schwanitz.tagging.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Lyrics3WritingStrategy Unit Tests")
class Lyrics3WritingStrategyTest {

    private Lyrics3WritingStrategy strategy;
    private TagParsingStrategyFactory parsingFactory;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parsingFactory = new TagParsingStrategyFactory();
        strategy = new Lyrics3WritingStrategy(parsingFactory);
    }

    @Test
    @DisplayName("getSupportedWriteFormats enthält LYRICS3V1 und LYRICS3V2")
    void supportedFormats() {
        assertTrue(strategy.getSupportedWriteFormats().contains(TagFormat.LYRICS3V1));
        assertTrue(strategy.getSupportedWriteFormats().contains(TagFormat.LYRICS3V2));
    }

    @Test
    @DisplayName("supportsInPlaceWrite ist false")
    void inPlace() {
        assertFalse(strategy.supportsInPlaceWrite(TagFormat.LYRICS3V1));
        assertFalse(strategy.supportsInPlaceWrite(TagFormat.LYRICS3V2));
    }

    @Test
    @DisplayName("writeTag für Lyrics3v2")
    void writeLyrics3v2() throws IOException {
        byte[] audioData = new byte[100];
        Path file = tempDir.resolve("test-lyrics.mp3");
        Files.write(file, audioData);

        GenericMetadata meta = new GenericMetadata(TagFormat.LYRICS3V2);
        meta.addField(new MetadataField<>("LYR", "Test Lyrics", new TextFieldHandler("LYR")));
        meta.addField(new MetadataField<>("AUT", "Test Author", new TextFieldHandler("AUT")));

        WriteConfiguration config = WriteConfiguration.defaults();

        try (SeekableDataSource source = com.schwanitz.io.SeekableDataSources.forPath(file)) {
            WriteResult result = strategy.writeTag(TagFormat.LYRICS3V2, meta, source, null, config);
            assertTrue(result.success());
            assertEquals(TagFormat.LYRICS3V2, result.format());
        }
    }

    @Test
    @DisplayName("writeTag für Lyrics3v1")
    void writeLyrics3v1() throws IOException {
        byte[] audioData = new byte[100];
        Path file = tempDir.resolve("test-lyrics-v1.mp3");
        Files.write(file, audioData);

        GenericMetadata meta = new GenericMetadata(TagFormat.LYRICS3V1);
        meta.addField(new MetadataField<>("LYRICS", "Simple Lyrics Text", new TextFieldHandler("LYRICS")));

        WriteConfiguration config = WriteConfiguration.defaults();

        try (SeekableDataSource source = com.schwanitz.io.SeekableDataSources.forPath(file)) {
            WriteResult result = strategy.writeTag(TagFormat.LYRICS3V1, meta, source, null, config);
            assertTrue(result.success());
        }
    }
}
