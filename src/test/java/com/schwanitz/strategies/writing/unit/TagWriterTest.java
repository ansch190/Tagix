package com.schwanitz.strategies.writing.unit;

import com.schwanitz.api.TagWriter;
import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.strategies.parsing.factory.TagParsingStrategyFactory;
import com.schwanitz.strategies.writing.factory.TagWritingStrategyFactory;
import com.schwanitz.tagging.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TagWriter Unit Tests")
class TagWriterTest {

    private TagWriter tagWriter;
    private TagFormatDetector detector;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        detector = new TagFormatDetector();
        TagParsingStrategyFactory parsingFactory = new TagParsingStrategyFactory();
        TagWritingStrategyFactory writingFactory = new TagWritingStrategyFactory(parsingFactory);
        tagWriter = new TagWriter(detector, parsingFactory, writingFactory);
    }

    @Test
    @DisplayName("Standard-Konstruktor funktioniert")
    void defaultConstructor() {
        TagWriter writer = new TagWriter();
        assertNotNull(writer);
    }

    @Test
    @DisplayName("writeTags mit Pfad und Metadaten")
    void writeTagsWithPath() throws IOException {
        byte[] audioData = createMP3WithID3v2_4();
        Path file = tempDir.resolve("write-tags.mp3");
        Files.write(file, audioData);

        GenericMetadata meta = new GenericMetadata(TagFormat.ID3V2_4);
        meta.addField(new MetadataField<>("TIT2", "New Title", new TextFieldHandler("TIT2")));
        meta.addField(new MetadataField<>("TPE1", "New Artist", new TextFieldHandler("TPE1")));

        WriteConfiguration config = WriteConfiguration.defaults();
        WriteResult result = tagWriter.writeTags(file.toString(), meta, config);

        assertNotNull(result);
        assertTrue(result.success(), "Schreibvorgang sollte erfolgreich sein: " + result.message());
    }

    @Test
    @DisplayName("writeTags mit Path-Objekt")
    void writeTagsWithNIOPath() throws IOException {
        byte[] audioData = createMP3WithID3v2_4();
        Path file = tempDir.resolve("write-nio.mp3");
        Files.write(file, audioData);

        GenericMetadata meta = new GenericMetadata(TagFormat.ID3V2_4);
        meta.addField(new MetadataField<>("TIT2", "NIO Title", new TextFieldHandler("TIT2")));

        WriteConfiguration config = WriteConfiguration.defaults();
        WriteResult result = tagWriter.writeTags(file, meta, config);

        assertNotNull(result);
        assertTrue(result.success());
    }

    @Test
    @DisplayName("updateField aktualisiert ein einzelnes Feld")
    void updateField() throws IOException {
        byte[] audioData = createMP3WithID3v2_4();
        Path file = tempDir.resolve("update-field.mp3");
        Files.write(file, audioData);

        WriteResult result = tagWriter.updateField(file.toString(), "TIT2", "Updated Title");

        assertNotNull(result);
        assertTrue(result.success(), "Update sollte erfolgreich sein: " + result.message());
    }

    @Test
    @DisplayName("removeTags entfernt Tags")
    void removeTags() throws IOException {
        byte[] audioData = createMP3WithID3v2_4();
        Path file = tempDir.resolve("remove-tags.mp3");
        Files.write(file, audioData);

        WriteResult result = tagWriter.removeTags(file.toString(), TagFormat.ID3V2_4);

        assertNotNull(result);
        // Entweder Erfolg oder "Kein Tag zum Entfernen"
        assertTrue(result.success());
    }

    @Test
    @DisplayName("removeTags mit Konfiguration")
    void removeTagsWithConfig() throws IOException {
        byte[] audioData = createMP3WithID3v2_4();
        Path file = tempDir.resolve("remove-config.mp3");
        Files.write(file, audioData);

        WriteConfiguration config = WriteConfiguration.remove();
        WriteResult result = tagWriter.removeTags(file.toString(), TagFormat.ID3V2_4, config);

        assertNotNull(result);
        assertTrue(result.success());
    }

    @Test
    @DisplayName("writeTags mit leeren Metadaten")
    void writeTagsEmptyMetadata() throws IOException {
        byte[] audioData = createMP3WithID3v2_4();
        Path file = tempDir.resolve("empty-meta.mp3");
        Files.write(file, audioData);

        GenericMetadata meta = new GenericMetadata(TagFormat.ID3V2_4);
        WriteConfiguration config = WriteConfiguration.createNew();

        WriteResult result = tagWriter.writeTags(file.toString(), meta, config);
        assertNotNull(result);
    }

    @Test
    @DisplayName("writeTags mit ReplaceAll-Konfiguration")
    void writeTagsReplaceAll() throws IOException {
        byte[] audioData = createMP3WithID3v2_4();
        Path file = tempDir.resolve("replace-all.mp3");
        Files.write(file, audioData);

        GenericMetadata meta = new GenericMetadata(TagFormat.ID3V2_4);
        meta.addField(new MetadataField<>("TIT2", "Replaced Title", new TextFieldHandler("TIT2")));

        WriteConfiguration config = WriteConfiguration.replaceAll();
        WriteResult result = tagWriter.writeTags(file.toString(), meta, config);

        assertNotNull(result);
        assertTrue(result.success());
    }

    @Test
    @DisplayName("Batch writeTags mit zwei Dateien")
    void batchWriteTags() throws IOException {
        byte[] audioData1 = createMP3WithID3v2_4();
        byte[] audioData2 = createMP3WithID3v2_4();
        Path file1 = tempDir.resolve("batch1.mp3");
        Path file2 = tempDir.resolve("batch2.mp3");
        Files.write(file1, audioData1);
        Files.write(file2, audioData2);

        GenericMetadata meta = new GenericMetadata(TagFormat.ID3V2_4);
        meta.addField(new MetadataField<>("TIT2", "Batch Title", new TextFieldHandler("TIT2")));

        List<String> filePaths = List.of(file1.toString(), file2.toString());
        Map<String, WriteResult> results = tagWriter.writeTags(filePaths, meta);

        assertNotNull(results);
        assertEquals(2, results.size());
        assertTrue(results.get(file1.toString()).success());
        assertTrue(results.get(file2.toString()).success());
    }

    @Test
    @DisplayName("Batch writeTags mit leerer Liste")
    void batchWriteTagsEmpty() {
        Map<String, WriteResult> results = tagWriter.writeTags(List.of(), new GenericMetadata(TagFormat.ID3V2_4));
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("writeTags mit SeekableDataSource")
    void writeTagsWithSource() throws IOException {
        byte[] audioData = createMP3WithID3v2_4();
        Path file = tempDir.resolve("source-test.mp3");
        Files.write(file, audioData);

        GenericMetadata meta = new GenericMetadata(TagFormat.ID3V2_4);
        meta.addField(new MetadataField<>("TIT2", "Source Title", new TextFieldHandler("TIT2")));

        WriteConfiguration config = WriteConfiguration.defaults();

        try (SeekableDataSource source = com.schwanitz.io.SeekableDataSources.forPath(file)) {
            WriteResult result = tagWriter.writeTags(source, meta, config);
            assertNotNull(result);
        }
    }

    /**
     * Erstellt ein minimales MP3 mit ID3v2.4 Header.
     */
    private byte[] createMP3WithID3v2_4() {
        byte[] data = new byte[200];
        // ID3v2 Header
        data[0] = 'I'; data[1] = 'D'; data[2] = '3';
        data[3] = 4; // Major version 4
        data[4] = 0; // Revision
        data[5] = 0; // Flags
        // Synchsafe size = 10 (minimaler Header)
        data[6] = 0; data[7] = 0; data[8] = 0; data[9] = 10;
        // Frame data (10 bytes)
        for (int i = 10; i < 20; i++) data[i] = 0;
        // Rest mit Audio-Füllbytes
        for (int i = 20; i < 200; i++) data[i] = (byte) 0xFF;
        return data;
    }
}
