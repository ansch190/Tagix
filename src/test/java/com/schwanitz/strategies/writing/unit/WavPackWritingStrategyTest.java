package com.schwanitz.strategies.writing.unit;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.strategies.parsing.factory.TagParsingStrategyFactory;
import com.schwanitz.strategies.writing.WavPackWritingStrategy;
import com.schwanitz.tagging.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WavPackWritingStrategy Unit Tests")
class WavPackWritingStrategyTest {

    private WavPackWritingStrategy strategy;
    private TagParsingStrategyFactory parsingFactory;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parsingFactory = new TagParsingStrategyFactory();
        strategy = new WavPackWritingStrategy(parsingFactory);
    }

    @Test
    @DisplayName("getSupportedWriteFormats enthält WAVPACK_NATIVE")
    void supportedFormats() {
        assertTrue(strategy.getSupportedWriteFormats().contains(TagFormat.WAVPACK_NATIVE));
    }

    @Test
    @DisplayName("supportsInPlaceWrite ist false")
    void inPlace() {
        assertFalse(strategy.supportsInPlaceWrite(TagFormat.WAVPACK_NATIVE));
    }

    @Test
    @DisplayName("writeTag für WavPack")
    void writeWavPack() throws IOException {
        byte[] audioData = new byte[100];
        Path file = tempDir.resolve("test.wv");
        Files.write(file, audioData);

        GenericMetadata meta = new GenericMetadata(TagFormat.WAVPACK_NATIVE);
        meta.addField(new MetadataField<>("Title", "WV Title", new TextFieldHandler("Title")));
        meta.addField(new MetadataField<>("Artist", "WV Artist", new TextFieldHandler("Artist")));

        WriteConfiguration config = WriteConfiguration.defaults();

        try (SeekableDataSource source = com.schwanitz.io.SeekableDataSources.forPath(file)) {
            WriteResult result = strategy.writeTag(TagFormat.WAVPACK_NATIVE, meta, source, null, config);
            assertTrue(result.success());
            assertEquals(TagFormat.WAVPACK_NATIVE, result.format());
        }
    }
}
