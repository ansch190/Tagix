package com.schwanitz.strategies.parsing.unit;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.strategies.parsing.MatroskaParsingStrategy;
import com.schwanitz.strategies.parsing.ParsingTestHelper;
import com.schwanitz.tagging.TagFormat;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

import static com.schwanitz.strategies.parsing.ParsingTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class MatroskaParsingStrategyTest {

    private MatroskaParsingStrategy strategy;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        strategy = new MatroskaParsingStrategy();
    }

    @Test
    @DisplayName("canHandle returns true for MATROSKA_TAGS and WEBM_TAGS")
    void canHandle_returnsTrueForMATROSKA_TAGS_and_WEBM_TAGS() {
        assertTrue(strategy.canHandle(TagFormat.MATROSKA_TAGS));
        assertTrue(strategy.canHandle(TagFormat.WEBM_TAGS));
    }

    @Test
    @DisplayName("canHandle returns false for other formats")
    void canHandle_returnsFalseForOtherFormats() {
        assertFalse(strategy.canHandle(TagFormat.ID3V2_3));
        assertFalse(strategy.canHandle(TagFormat.MP4));
        assertFalse(strategy.canHandle(TagFormat.DSF_METADATA));
        assertFalse(strategy.canHandle(TagFormat.WAVPACK_NATIVE));
    }

    private byte[] buildSimpleTag(String name, String value) {
        byte[] nameData = utf8(name);
        byte[] valueData = utf8(value);
        byte[] nameElement = concat(buildEBMLVLI(0x45A3), buildEBMLVLI(nameData.length), nameData);
        byte[] valueElement = concat(buildEBMLVLI(0x4487), buildEBMLVLI(valueData.length), valueData);
        byte[] content = concat(nameElement, valueElement);
        return concat(buildEBMLVLI(0x67C8), buildEBMLVLI(content.length), content);
    }

    private byte[] buildTagsBlock(byte[]... tagElements) {
        byte[] tagContent = concat(tagElements);
        byte[] tagElement = concat(buildEBMLVLI(0x7373), buildEBMLVLI(tagContent.length), tagContent);
        byte[] tagsContent = tagElement;
        return concat(new byte[]{0x12, 0x54, (byte) 0xC3, 0x67}, buildEBMLVLI(tagsContent.length), tagsContent);
    }

    @Test
    @DisplayName("parseSimpleTag extracts title and artist")
    void parseSimpleTag_extractsTitleAndArtist() throws IOException {
        byte[] tagsBlock = buildTagsBlock(
                buildSimpleTag("TITLE", "Test Song"),
                buildSimpleTag("ARTIST", "Test Artist")
        );
        File file = writeTempFile(tempDir.toFile(), "test.mkv", tagsBlock);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.MATROSKA_TAGS, raf, 0, tagsBlock.length);

            assertEquals("Test Song", ParsingTestHelper.findFieldValue(metadata, "Title"));
            assertEquals("Test Artist", ParsingTestHelper.findFieldValue(metadata, "Artist"));
        }
    }

    @Test
    @DisplayName("parseWithEmptyValue adds no field")
    void parseWithEmptyValue_noFieldAdded() throws IOException {
        byte[] nameData = utf8("TITLE");
        byte[] nameElement = concat(buildEBMLVLI(0x45A3), buildEBMLVLI(nameData.length), nameData);
        byte[] valueElement = concat(buildEBMLVLI(0x4487), buildEBMLVLI(0));
        byte[] simpleTagContent = concat(nameElement, valueElement);
        byte[] simpleTag = concat(buildEBMLVLI(0x67C8), buildEBMLVLI(simpleTagContent.length), simpleTagContent);
        byte[] tagsBlock = buildTagsBlock(simpleTag);
        File file = writeTempFile(tempDir.toFile(), "test.mkv", tagsBlock);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.MATROSKA_TAGS, raf, 0, tagsBlock.length);

            assertFalse(ParsingTestHelper.hasField(metadata, "Title"));
        }
    }
}
