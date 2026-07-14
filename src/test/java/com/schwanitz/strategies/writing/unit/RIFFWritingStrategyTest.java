package com.schwanitz.strategies.writing.unit;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.strategies.parsing.factory.TagParsingStrategyFactory;
import com.schwanitz.strategies.writing.RIFFWritingStrategy;
import com.schwanitz.tagging.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RIFFWritingStrategy Unit Tests")
class RIFFWritingStrategyTest {

    private RIFFWritingStrategy strategy;
    private TagParsingStrategyFactory parsingFactory;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parsingFactory = new TagParsingStrategyFactory();
        strategy = new RIFFWritingStrategy(parsingFactory);
    }

    @Test
    @DisplayName("getSupportedWriteFormats enthält RIFF_INFO und BWF")
    void supportedFormats() {
        assertTrue(strategy.getSupportedWriteFormats().contains(TagFormat.RIFF_INFO));
        assertTrue(strategy.getSupportedWriteFormats().contains(TagFormat.BWF_V0));
        assertTrue(strategy.getSupportedWriteFormats().contains(TagFormat.BWF_V1));
        assertTrue(strategy.getSupportedWriteFormats().contains(TagFormat.BWF_V2));
    }

    @Test
    @DisplayName("supportsInPlaceWrite ist false")
    void inPlace() {
        assertFalse(strategy.supportsInPlaceWrite(TagFormat.RIFF_INFO));
    }

    @Test
    @DisplayName("writeTag für RIFF INFO")
    void writeRIFFInfo() throws IOException {
        byte[] audioData = new byte[100];
        Path file = tempDir.resolve("test.wav");
        Files.write(file, audioData);

        GenericMetadata meta = new GenericMetadata(TagFormat.RIFF_INFO);
        meta.addField(new MetadataField<>("INAM", "WAV Title", new TextFieldHandler("INAM")));
        meta.addField(new MetadataField<>("IART", "WAV Artist", new TextFieldHandler("IART")));

        WriteConfiguration config = WriteConfiguration.defaults();

        try (SeekableDataSource source = com.schwanitz.io.SeekableDataSources.forPath(file)) {
            WriteResult result = strategy.writeTag(TagFormat.RIFF_INFO, meta, source, null, config);
            assertTrue(result.success());
        }
    }

    @Test
    @DisplayName("writeTag für BWF")
    void writeBWF() throws IOException {
        byte[] audioData = new byte[100];
        Path file = tempDir.resolve("test-bwf.wav");
        Files.write(file, audioData);

        GenericMetadata meta = new GenericMetadata(TagFormat.BWF_V1);
        meta.addField(new MetadataField<>("Description", "BWF Description", new TextFieldHandler("Description")));
        meta.addField(new MetadataField<>("Originator", "Test Originator", new TextFieldHandler("Originator")));

        WriteConfiguration config = WriteConfiguration.defaults();

        try (SeekableDataSource source = com.schwanitz.io.SeekableDataSources.forPath(file)) {
            WriteResult result = strategy.writeTag(TagFormat.BWF_V1, meta, source, null, config);
            assertTrue(result.success());
        }
    }
}
