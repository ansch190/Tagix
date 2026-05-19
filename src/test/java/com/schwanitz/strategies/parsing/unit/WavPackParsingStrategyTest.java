package com.schwanitz.strategies.parsing.unit;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.strategies.parsing.ParsingTestHelper;
import com.schwanitz.strategies.parsing.WavPackParsingStrategy;
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
class WavPackParsingStrategyTest {

    private WavPackParsingStrategy strategy;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        strategy = new WavPackParsingStrategy();
    }

    @Test
    @DisplayName("parseMD5 sub-block returns MD5 string")
    void parseMD5SubBlock_returnsMD5String() throws IOException {
        byte[] md5Data = new byte[16];
        for (int i = 0; i < 16; i++) md5Data[i] = (byte) i;
        byte[] subBlock = buildWavPackSubBlock(0x26, md5Data, false);
        File file = writeTempFile(tempDir.toFile(), "test.wv", subBlock);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf)) {
            Metadata metadata = strategy.parseTag(TagFormat.WAVPACK_NATIVE, source, 0, subBlock.length);

            String fieldValue = ParsingTestHelper.findFieldValue(metadata, "MD5Checksum");
            assertNotNull(fieldValue);
            assertEquals("000102030405060708090a0b0c0d0e0f", fieldValue);
        }
    }

    @Test
    @DisplayName("parseConfiguration sub-block returns config info")
    void parseConfigurationSubBlock_returnsConfigInfo() throws IOException {
        byte[] configData = new byte[4];
        byte[] subBlock = buildWavPackSubBlock(0x25, configData, false);
        File file = writeTempFile(tempDir.toFile(), "test.wv", subBlock);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf)) {
            Metadata metadata = strategy.parseTag(TagFormat.WAVPACK_NATIVE, source, 0, subBlock.length);

            String fieldValue = ParsingTestHelper.findFieldValue(metadata, "ConfigurationBlock");
            assertNotNull(fieldValue);
            assertTrue(fieldValue.contains("bytes of configuration data"));
        }
    }

    @Test
    @DisplayName("parseUnknown sub-block returns byte size info")
    void parseUnknownSubBlock_returnsByteSizeInfo() throws IOException {
        byte[] unknownData = new byte[8];
        byte[] subBlock = buildWavPackSubBlock(0x10, unknownData, false);
        File file = writeTempFile(tempDir.toFile(), "test.wv", subBlock);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf)) {
            Metadata metadata = strategy.parseTag(TagFormat.WAVPACK_NATIVE, source, 0, subBlock.length);

            String fieldValue = ParsingTestHelper.findFieldValue(metadata, "Unknown_16");
            assertNotNull(fieldValue);
            assertEquals("[8 bytes]", fieldValue);
        }
    }
}
