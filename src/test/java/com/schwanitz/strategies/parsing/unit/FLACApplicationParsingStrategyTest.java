package com.schwanitz.strategies.parsing.unit;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.strategies.parsing.FLACApplicationParsingStrategy;
import com.schwanitz.strategies.parsing.ParsingTestHelper;
import com.schwanitz.tagging.TagFormat;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.io.SeekableDataSources;
import java.nio.file.Path;

import static com.schwanitz.strategies.parsing.ParsingTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class FLACApplicationParsingStrategyTest {

    private FLACApplicationParsingStrategy strategy;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        strategy = new FLACApplicationParsingStrategy();
    }

    @Test
    @DisplayName("parseKnownApplicationId returns app name")
    void parseKnownApplicationId_returnsAppName() throws IOException {
        byte[] appData = new byte[]{0x01, 0x02, 0x03, 0x04};
        byte[] block = buildFLACApplicationBlock("ATCH", appData, false);
        File file = writeTempFile(tempDir.toFile(), "test.flac", block);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf)) {
            Metadata metadata = strategy.parseTag(TagFormat.FLAC_APPLICATION, source, 0, block.length);

            String fieldValue = ParsingTestHelper.findFieldValue(metadata, "ApplicationId");
            assertNotNull(fieldValue);
            assertTrue(fieldValue.contains("ATCH"));
        }
    }

    @Test
    @DisplayName("parseUnknownApplicationId returns unknown format")
    void parseUnknownApplicationId_returnsUnknownFormat() throws IOException {
        byte[] appData = new byte[]{0x01, 0x02};
        byte[] block = buildFLACApplicationBlock("ZZZZ", appData, false);
        File file = writeTempFile(tempDir.toFile(), "test.flac", block);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf)) {
            Metadata metadata = strategy.parseTag(TagFormat.FLAC_APPLICATION, source, 0, block.length);

            String fieldValue = ParsingTestHelper.findFieldValue(metadata, "ApplicationId");
            assertNotNull(fieldValue);
            assertTrue(fieldValue.contains("Unknown"));
        }
    }

    @Test
    @DisplayName("parseInvalidBlockType returns empty metadata")
    void parseInvalidBlockType_returnsEmptyMetadata() throws IOException {
        byte[] block = new byte[4 + 4 + 4];
        block[0] = 0x01;
        block[1] = 0x00;
        block[2] = 0x00;
        block[3] = 0x08;
        System.arraycopy(ascii("ATCH"), 0, block, 4, 4);
        File file = writeTempFile(tempDir.toFile(), "test.flac", block);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf)) {
            Metadata metadata = strategy.parseTag(TagFormat.FLAC_APPLICATION, source, 0, block.length);

            assertNotNull(metadata);
            assertEquals(0, metadata.getFields().size());
        }
    }

    @Test
    @DisplayName("parseBlockTooSmall returns empty metadata")
    void parseBlockTooSmall_returnsEmptyMetadata() throws IOException {
        byte[] block = new byte[4];
        block[0] = 0x02;
        block[1] = 0x00;
        block[2] = 0x00;
        block[3] = 0x02;
        File file = writeTempFile(tempDir.toFile(), "test.flac", block);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf)) {
            Metadata metadata = strategy.parseTag(TagFormat.FLAC_APPLICATION, source, 0, block.length);

            assertNotNull(metadata);
            assertEquals(0, metadata.getFields().size());
        }
    }
}
