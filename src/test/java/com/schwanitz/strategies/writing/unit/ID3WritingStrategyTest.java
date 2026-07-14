package com.schwanitz.strategies.writing.unit;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.strategies.parsing.factory.TagParsingStrategyFactory;
import com.schwanitz.strategies.writing.ID3WritingStrategy;
import com.schwanitz.tagging.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ID3WritingStrategy Unit Tests")
class ID3WritingStrategyTest {

    private ID3WritingStrategy strategy;
    private TagParsingStrategyFactory parsingFactory;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parsingFactory = new TagParsingStrategyFactory();
        strategy = new ID3WritingStrategy(parsingFactory);
    }

    @Test
    @DisplayName("getSupportedWriteFormats enthält alle ID3-Formate")
    void supportedFormats() {
        var formats = strategy.getSupportedWriteFormats();
        assertTrue(formats.contains(TagFormat.ID3V1));
        assertTrue(formats.contains(TagFormat.ID3V1_1));
        assertTrue(formats.contains(TagFormat.ID3V2_2));
        assertTrue(formats.contains(TagFormat.ID3V2_3));
        assertTrue(formats.contains(TagFormat.ID3V2_4));
    }

    @Test
    @DisplayName("supportsInPlaceWrite: ID3V1 unterstützt In-Place")
    void inPlaceV1() {
        assertTrue(strategy.supportsInPlaceWrite(TagFormat.ID3V1));
        assertTrue(strategy.supportsInPlaceWrite(TagFormat.ID3V1_1));
    }

    @Test
    @DisplayName("supportsInPlaceWrite: ID3v2 unterstützt kein In-Place")
    void inPlaceV2() {
        assertFalse(strategy.supportsInPlaceWrite(TagFormat.ID3V2_2));
        assertFalse(strategy.supportsInPlaceWrite(TagFormat.ID3V2_3));
        assertFalse(strategy.supportsInPlaceWrite(TagFormat.ID3V2_4));
    }

    @Test
    @DisplayName("writeTag für ID3V2_4 mit Metadaten erzeugt Erfolg")
    void writeId3v24() throws IOException {
        byte[] audioData = new byte[100];
        audioData[0] = 'I'; audioData[1] = 'D'; audioData[2] = '3';
        // ID3v2.4 Header
        audioData[3] = 4; // Major version
        audioData[4] = 0; // Revision
        audioData[5] = 0; // Flags
        // Synchsafe size = 10
        audioData[6] = 0; audioData[7] = 0; audioData[8] = 0; audioData[9] = 10;
        // Frame data
        for (int i = 10; i < 100; i++) audioData[i] = 0;

        Path file = tempDir.resolve("test.mp3");
        Files.write(file, audioData);

        GenericMetadata meta = new GenericMetadata(TagFormat.ID3V2_4);
        meta.addField(new MetadataField<>("TIT2", "Test Title", new TextFieldHandler("TIT2")));
        meta.addField(new MetadataField<>("TPE1", "Test Artist", new TextFieldHandler("TPE1")));

        WriteConfiguration config = WriteConfiguration.defaults();
        TagInfo existingTag = new TagInfo(TagFormat.ID3V2_4, 0, 110);

        try (SeekableDataSource source = com.schwanitz.io.SeekableDataSources.forPath(file)) {
            WriteResult result = strategy.writeTag(TagFormat.ID3V2_4, meta, source, existingTag, config);
            assertTrue(result.success(), "Schreibvorgang sollte erfolgreich sein");
            assertEquals(TagFormat.ID3V2_4, result.format());
        }
    }

    @Test
    @DisplayName("writeTag für ID3V1 schreibt Metadaten")
    void writeId3v1() throws IOException {
        byte[] audioData = new byte[100];
        Path file = tempDir.resolve("test-v1.mp3");
        Files.write(file, audioData);

        GenericMetadata meta = new GenericMetadata(TagFormat.ID3V1);
        meta.addField(new MetadataField<>("TIT2", "My Title", new TextFieldHandler("TIT2")));
        meta.addField(new MetadataField<>("TPE1", "My Artist", new TextFieldHandler("TPE1")));
        meta.addField(new MetadataField<>("TALB", "My Album", new TextFieldHandler("TALB")));
        meta.addField(new MetadataField<>("TCON", "Rock", new TextFieldHandler("TCON")));

        WriteConfiguration config = WriteConfiguration.defaults();
        TagInfo existingTag = new TagInfo(TagFormat.ID3V1, 100, 128);

        try (SeekableDataSource source = com.schwanitz.io.SeekableDataSources.forPath(file)) {
            WriteResult result = strategy.writeTag(TagFormat.ID3V1, meta, source, existingTag, config);
            assertTrue(result.success());
            assertEquals(TagFormat.ID3V1, result.format());
        }
    }

    @Test
    @DisplayName("writeTag für nicht unterstütztes Format gibt Fehler")
    void unsupportedFormat() throws IOException {
        byte[] audioData = new byte[10];
        Path file = tempDir.resolve("test.mp3");
        Files.write(file, audioData);

        GenericMetadata meta = new GenericMetadata(TagFormat.MP4);
        WriteConfiguration config = WriteConfiguration.defaults();

        try (SeekableDataSource source = com.schwanitz.io.SeekableDataSources.forPath(file)) {
            WriteResult result = strategy.writeTag(TagFormat.MP4, meta, source, null, config);
            assertFalse(result.success());
            assertNotNull(result.message());
        }
    }
}
