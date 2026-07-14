package com.schwanitz.strategies.writing.unit;

import com.schwanitz.io.SeekableDataSources;
import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.strategies.parsing.factory.TagParsingStrategyFactory;
import com.schwanitz.strategies.writing.AbstractTagWritingStrategy;
import com.schwanitz.tagging.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AbstractTagWritingStrategy Tests")
class AbstractTagWritingStrategyTest {

    private TestWritingStrategy strategy;
    private TagParsingStrategyFactory parsingFactory;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parsingFactory = new TagParsingStrategyFactory();
        strategy = new TestWritingStrategy(parsingFactory);
    }

    @Test
    @DisplayName("mergeMetadata: neue Felder überschreiben bestehende")
    void mergeMetadata_newFieldsOverrideExisting() {
        GenericMetadata existing = new GenericMetadata(TagFormat.ID3V2_4);
        existing.addField(new MetadataField<>("TIT2", "Alter Titel", new TextFieldHandler("TIT2")));
        existing.addField(new MetadataField<>("TPE1", "Alter Künstler", new TextFieldHandler("TPE1")));

        GenericMetadata newMeta = new GenericMetadata(TagFormat.ID3V2_4);
        newMeta.addField(new MetadataField<>("TIT2", "Neuer Titel", new TextFieldHandler("TIT2")));

        Metadata merged = strategy.mergeMetadataPublic(existing, newMeta);

        assertEquals(2, merged.getFields().size());
        assertEquals("Neuer Titel", findValue(merged, "TIT2"));
        assertEquals("Alter Künstler", findValue(merged, "TPE1"));
    }

    @Test
    @DisplayName("mergeMetadata: null existing gibt newMeta zurück")
    void mergeMetadata_nullExisting() {
        GenericMetadata newMeta = new GenericMetadata(TagFormat.ID3V2_4);
        newMeta.addField(new MetadataField<>("TIT2", "Titel", new TextFieldHandler("TIT2")));

        Metadata merged = strategy.mergeMetadataPublic(null, newMeta);

        assertEquals(1, merged.getFields().size());
        assertEquals("Titel", findValue(merged, "TIT2"));
    }

    @Test
    @DisplayName("mergeMetadata: null newMeta gibt existing zurück")
    void mergeMetadata_nullNew() {
        GenericMetadata existing = new GenericMetadata(TagFormat.ID3V2_4);
        existing.addField(new MetadataField<>("TIT2", "Titel", new TextFieldHandler("TIT2")));

        Metadata merged = strategy.mergeMetadataPublic(existing, null);

        assertEquals(1, merged.getFields().size());
        assertEquals("Titel", findValue(merged, "TIT2"));
    }

    @Test
    @DisplayName("validateInput: wirft NullPointerException bei null metadata")
    void validateInput_nullMetadata() {
        assertThrows(NullPointerException.class, () ->
                strategy.validateInputPublic(null,
                        SeekableDataSources.forBytes(new byte[10]),
                        TagFormat.ID3V2_4));
    }

    @Test
    @DisplayName("validateInput: wirft NullPointerException bei null source")
    void validateInput_nullSource() {
        GenericMetadata meta = new GenericMetadata(TagFormat.ID3V2_4);
        assertThrows(NullPointerException.class, () ->
                strategy.validateInputPublic(meta, null, TagFormat.ID3V2_4));
    }

    @Test
    @DisplayName("validateInput: Erfolg bei gültigen Eingaben")
    void validateInput_valid() {
        GenericMetadata meta = new GenericMetadata(TagFormat.ID3V2_4);
        assertDoesNotThrow(() ->
                strategy.validateInputPublic(meta,
                        SeekableDataSources.forBytes(new byte[10]),
                        TagFormat.ID3V2_4));
    }

    @Test
    @DisplayName("getStrategyName gibt korrekten Namen zurück")
    void getStrategyName() {
        assertEquals("Test", strategy.getStrategyNamePublic());
    }

    private String findValue(Metadata meta, String key) {
        for (MetadataField<?> field : meta.getFields()) {
            if (field.getKey().equals(key)) {
                return field.getValue() != null ? field.getValue().toString() : null;
            }
        }
        return null;
    }

    /**
     * Testbare Subklasse die protected Methoden public macht.
     */
    static class TestWritingStrategy extends AbstractTagWritingStrategy {

        TestWritingStrategy(TagParsingStrategyFactory parsingFactory) {
            super("Test", parsingFactory);
        }

        @Override
        public WriteResult writeTag(TagFormat format, Metadata metadata,
                                    SeekableDataSource source, TagInfo existingTag,
                                    WriteConfiguration config) {
            return WriteResult.success(format, 0, 0);
        }

        @Override
        public java.util.List<TagFormat> getSupportedWriteFormats() {
            return java.util.List.of(TagFormat.ID3V2_4);
        }

        @Override
        public boolean supportsInPlaceWrite(TagFormat format) {
            return false;
        }

        public Metadata mergeMetadataPublic(Metadata existing, Metadata newMeta) {
            return mergeMetadata(existing, newMeta);
        }

        public void validateInputPublic(Metadata metadata, SeekableDataSource source, TagFormat format) {
            validateInput(metadata, source, format);
        }

        public String getStrategyNamePublic() {
            return getStrategyName();
        }
    }
}
