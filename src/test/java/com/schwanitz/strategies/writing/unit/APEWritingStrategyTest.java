package com.schwanitz.strategies.writing.unit;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.strategies.parsing.factory.TagParsingStrategyFactory;
import com.schwanitz.strategies.writing.APEWritingStrategy;
import com.schwanitz.tagging.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("APEWritingStrategy Unit Tests")
class APEWritingStrategyTest {

    private APEWritingStrategy strategy;
    private TagParsingStrategyFactory parsingFactory;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parsingFactory = new TagParsingStrategyFactory();
        strategy = new APEWritingStrategy(parsingFactory);
    }

    @Test
    @DisplayName("getSupportedWriteFormats enthält APEV1 und APEV2")
    void supportedFormats() {
        assertTrue(strategy.getSupportedWriteFormats().contains(TagFormat.APEV1));
        assertTrue(strategy.getSupportedWriteFormats().contains(TagFormat.APEV2));
    }

    @Test
    @DisplayName("supportsInPlaceWrite ist true für APE")
    void inPlace() {
        assertTrue(strategy.supportsInPlaceWrite(TagFormat.APEV1));
        assertTrue(strategy.supportsInPlaceWrite(TagFormat.APEV2));
    }

    @Test
    @DisplayName("writeTag für APEV2 mit Metadaten")
    void writeApev2() throws IOException {
        byte[] audioData = new byte[100];
        Path file = tempDir.resolve("test.ape");
        Files.write(file, audioData);

        GenericMetadata meta = new GenericMetadata(TagFormat.APEV2);
        meta.addField(new MetadataField<>("Title", "APE Title", new TextFieldHandler("Title")));
        meta.addField(new MetadataField<>("Artist", "APE Artist", new TextFieldHandler("Artist")));
        meta.addField(new MetadataField<>("Album", "APE Album", new TextFieldHandler("Album")));

        WriteConfiguration config = WriteConfiguration.defaults();
        TagInfo existingTag = new TagInfo(TagFormat.APEV2, 100, 64);

        try (SeekableDataSource source = com.schwanitz.io.SeekableDataSources.forPath(file)) {
            WriteResult result = strategy.writeTag(TagFormat.APEV2, meta, source, existingTag, config);
            assertTrue(result.success());
            assertEquals(TagFormat.APEV2, result.format());
            assertTrue(result.newTagSize() > 0);
        }
    }

    @Test
    @DisplayName("writeTag für APEV1 mit Metadaten")
    void writeApev1() throws IOException {
        byte[] audioData = new byte[100];
        Path file = tempDir.resolve("test-v1.ape");
        Files.write(file, audioData);

        GenericMetadata meta = new GenericMetadata(TagFormat.APEV1);
        meta.addField(new MetadataField<>("Title", "APEv1 Title", new TextFieldHandler("Title")));

        WriteConfiguration config = WriteConfiguration.defaults();

        try (SeekableDataSource source = com.schwanitz.io.SeekableDataSources.forPath(file)) {
            WriteResult result = strategy.writeTag(TagFormat.APEV1, meta, source, null, config);
            assertTrue(result.success());
        }
    }
}
