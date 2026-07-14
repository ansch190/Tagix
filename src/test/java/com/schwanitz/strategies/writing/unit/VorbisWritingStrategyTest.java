package com.schwanitz.strategies.writing.unit;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.strategies.parsing.factory.TagParsingStrategyFactory;
import com.schwanitz.strategies.writing.VorbisWritingStrategy;
import com.schwanitz.tagging.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VorbisWritingStrategy Unit Tests")
class VorbisWritingStrategyTest {

    private VorbisWritingStrategy strategy;
    private TagParsingStrategyFactory parsingFactory;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parsingFactory = new TagParsingStrategyFactory();
        strategy = new VorbisWritingStrategy(parsingFactory);
    }

    @Test
    @DisplayName("getSupportedWriteFormats enthält VORBIS_COMMENT")
    void supportedFormats() {
        assertTrue(strategy.getSupportedWriteFormats().contains(TagFormat.VORBIS_COMMENT));
    }

    @Test
    @DisplayName("supportsInPlaceWrite ist false")
    void inPlace() {
        assertFalse(strategy.supportsInPlaceWrite(TagFormat.VORBIS_COMMENT));
    }

    @Test
    @DisplayName("writeTag für OGG Vorbis mit Metadaten")
    void writeOGG() throws IOException {
        byte[] audioData = new byte[100];
        audioData[0] = 'O'; audioData[1] = 'g'; audioData[2] = 'g'; audioData[3] = 'S';

        Path file = tempDir.resolve("test.ogg");
        Files.write(file, audioData);

        GenericMetadata meta = new GenericMetadata(TagFormat.VORBIS_COMMENT);
        meta.addField(new MetadataField<>("TITLE", "OGG Title", new TextFieldHandler("TITLE")));
        meta.addField(new MetadataField<>("ARTIST", "OGG Artist", new TextFieldHandler("ARTIST")));

        WriteConfiguration config = WriteConfiguration.defaults();

        try (SeekableDataSource source = com.schwanitz.io.SeekableDataSources.forPath(file)) {
            WriteResult result = strategy.writeTag(TagFormat.VORBIS_COMMENT, meta, source, null, config);
            assertTrue(result.success(), "Schreibvorgang sollte erfolgreich sein");
            assertEquals(TagFormat.VORBIS_COMMENT, result.format());
        }
    }

    @Test
    @DisplayName("writeTag für FLAC mit Metadaten")
    void writeFLAC() throws IOException {
        byte[] audioData = new byte[100];
        audioData[0] = 'f'; audioData[1] = 'L'; audioData[2] = 'a'; audioData[3] = 'C';

        Path file = tempDir.resolve("test.flac");
        Files.write(file, audioData);

        GenericMetadata meta = new GenericMetadata(TagFormat.VORBIS_COMMENT);
        meta.addField(new MetadataField<>("TITLE", "FLAC Title", new TextFieldHandler("TITLE")));

        WriteConfiguration config = WriteConfiguration.defaults();

        try (SeekableDataSource source = com.schwanitz.io.SeekableDataSources.forPath(file)) {
            WriteResult result = strategy.writeTag(TagFormat.VORBIS_COMMENT, meta, source, null, config);
            assertTrue(result.success());
        }
    }

    @Test
    @DisplayName("writeTag für nicht OGG/FLAC gibt Fehler")
    void unsupportedFileType() throws IOException {
        byte[] audioData = new byte[100];
        audioData[0] = 'R'; audioData[1] = 'I'; audioData[2] = 'F'; audioData[3] = 'F';

        Path file = tempDir.resolve("test.wav");
        Files.write(file, audioData);

        GenericMetadata meta = new GenericMetadata(TagFormat.VORBIS_COMMENT);
        WriteConfiguration config = WriteConfiguration.defaults();

        try (SeekableDataSource source = com.schwanitz.io.SeekableDataSources.forPath(file)) {
            WriteResult result = strategy.writeTag(TagFormat.VORBIS_COMMENT, meta, source, null, config);
            assertFalse(result.success());
        }
    }
}
