package com.schwanitz.strategies.parsing.unit;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.strategies.parsing.ASFParsingStrategy;
import com.schwanitz.strategies.parsing.ParsingTestHelper;
import com.schwanitz.strategies.parsing.ParsingTestHelper.ASFAttribute;
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
class ASFParsingStrategyTest {

    private ASFParsingStrategy strategy;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        strategy = new ASFParsingStrategy();
    }

    @Test
    @DisplayName("canHandle returns true for ASF_CONTENT_DESC and ASF_EXT_CONTENT_DESC")
    void canHandle_ASF_true() {
        assertTrue(strategy.canHandle(TagFormat.ASF_CONTENT_DESC));
        assertTrue(strategy.canHandle(TagFormat.ASF_EXT_CONTENT_DESC));
    }

    @Test
    @DisplayName("canHandle returns false for non-ASF formats")
    void canHandle_nonASF_false() {
        assertFalse(strategy.canHandle(TagFormat.MP4));
        assertFalse(strategy.canHandle(TagFormat.RIFF_INFO));
        assertFalse(strategy.canHandle(TagFormat.ID3V2_3));
    }

    @Test
    @DisplayName("parseContentDescription extracts title, author, copyright, description, rating")
    void parseContentDescription() throws IOException {
        byte[] data = buildASFContentDescObject("Test Title", "Test Author", "Test Copyright",
                "Test Description", "Test Rating");
        File file = writeTempFile(tempDir.toFile(), "test.asf", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.ASF_CONTENT_DESC, raf, 0, data.length);

            assertEquals("Test Title", ParsingTestHelper.findFieldValue(metadata, "Title"));
            assertEquals("Test Author", ParsingTestHelper.findFieldValue(metadata, "Artist"));
            assertEquals("Test Copyright", ParsingTestHelper.findFieldValue(metadata, "Copyright"));
            assertEquals("Test Description", ParsingTestHelper.findFieldValue(metadata, "Description"));
            assertEquals("Test Rating", ParsingTestHelper.findFieldValue(metadata, "Rating"));
        }
    }

    @Test
    @DisplayName("parseExtendedContentDescription extracts multiple attribute types")
    void parseExtendedContentDescription() throws IOException {
        byte[] data = buildASFExtContentDescObject(
                ASFAttribute.string("WM/AlbumTitle", "Test Album"),
                ASFAttribute.dword("WM/TrackNumber", 5),
                ASFAttribute.qword("WM/Year", 2024L),
                ASFAttribute.word("WM/Genre", 17),
                ASFAttribute.bool("IsCompilation", true)
        );
        File file = writeTempFile(tempDir.toFile(), "test.asf", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.ASF_EXT_CONTENT_DESC, raf, 0, data.length);

            assertEquals("Test Album", ParsingTestHelper.findFieldValue(metadata, "Album"));
            assertEquals("5", ParsingTestHelper.findFieldValue(metadata, "TrackNumber"));
            assertEquals("2024", ParsingTestHelper.findFieldValue(metadata, "Year"));
            assertEquals("17", ParsingTestHelper.findFieldValue(metadata, "Genre"));
            assertEquals("true", ParsingTestHelper.findFieldValue(metadata, "IsCompilation"));
        }
    }

    @Test
    @DisplayName("emptyContentDescription returns empty metadata when all zero lengths")
    void emptyContentDescription_returnsEmptyMetadata() throws IOException {
        byte[] data = buildASFContentDescObject(null, null, null, null, null);
        File file = writeTempFile(tempDir.toFile(), "test.asf", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.ASF_CONTENT_DESC, raf, 0, data.length);

            assertNotNull(metadata);
            assertEquals(0, metadata.getFields().size());
        }
    }

    @Test
    @DisplayName("parseStringType extracts ASF_TYPE_STRING=0")
    void parseStringType() throws IOException {
        byte[] data = buildASFExtContentDescObject(
                ASFAttribute.string("WM/AlbumTitle", "String Value")
        );
        File file = writeTempFile(tempDir.toFile(), "test.asf", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.ASF_EXT_CONTENT_DESC, raf, 0, data.length);

            assertEquals("String Value", ParsingTestHelper.findFieldValue(metadata, "Album"));
        }
    }

    @Test
    @DisplayName("parseDWORDType extracts ASF_TYPE_DWORD=3")
    void parseDWORDType() throws IOException {
        byte[] data = buildASFExtContentDescObject(
                ASFAttribute.dword("WM/TrackNumber", 42)
        );
        File file = writeTempFile(tempDir.toFile(), "test.asf", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.ASF_EXT_CONTENT_DESC, raf, 0, data.length);

            assertEquals("42", ParsingTestHelper.findFieldValue(metadata, "TrackNumber"));
        }
    }

    @Test
    @DisplayName("parseQWORDType extracts ASF_TYPE_QWORD=4")
    void parseQWORDType() throws IOException {
        byte[] data = buildASFExtContentDescObject(
                ASFAttribute.qword("WM/Year", 2024L)
        );
        File file = writeTempFile(tempDir.toFile(), "test.asf", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.ASF_EXT_CONTENT_DESC, raf, 0, data.length);

            assertEquals("2024", ParsingTestHelper.findFieldValue(metadata, "Year"));
        }
    }

    @Test
    @DisplayName("parseWORDType extracts ASF_TYPE_WORD=5")
    void parseWORDType() throws IOException {
        byte[] data = buildASFExtContentDescObject(
                ASFAttribute.word("WM/Genre", 99)
        );
        File file = writeTempFile(tempDir.toFile(), "test.asf", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.ASF_EXT_CONTENT_DESC, raf, 0, data.length);

            assertEquals("99", ParsingTestHelper.findFieldValue(metadata, "Genre"));
        }
    }

    @Test
    @DisplayName("parseBoolType extracts ASF_TYPE_BOOL=6")
    void parseBoolType() throws IOException {
        byte[] data = buildASFExtContentDescObject(
                ASFAttribute.bool("IsCompilation", true),
                ASFAttribute.bool("IsPrivate", false)
        );
        File file = writeTempFile(tempDir.toFile(), "test.asf", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.ASF_EXT_CONTENT_DESC, raf, 0, data.length);

            assertEquals("true", ParsingTestHelper.findFieldValue(metadata, "IsCompilation"));
            assertEquals("false", ParsingTestHelper.findFieldValue(metadata, "IsPrivate"));
        }
    }
}