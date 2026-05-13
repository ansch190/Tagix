package com.schwanitz.strategies.detection.unit;

import com.schwanitz.strategies.detection.FLACApplicationDetectionStrategy;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.schwanitz.strategies.detection.DetectionTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FLAC Application Detection Strategy")
class FLACApplicationDetectionStrategyTest {

    private FLACApplicationDetectionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new FLACApplicationDetectionStrategy();
    }

    @Nested
    @DisplayName("canDetect")
    class CanDetect {

        @Test
        @DisplayName("fLaC signature")
        void flacSignature() throws Exception {
            byte[] data = builder().writeString("fLaC").writeBytes(100).build();
            Buffers bufs = readBuffers(forBytes(data));
            assertTrue(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("Invalid signature")
        void invalidSignature() throws Exception {
            byte[] data = builder().writeString("FLAC").writeBytes(100).build();
            Buffers bufs = readBuffers(forBytes(data));
            assertFalse(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("Buffer too short")
        void bufferTooShort() throws Exception {
            byte[] data = new byte[2];
            Buffers bufs = readBuffers(forBytes(data));
            assertFalse(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }
    }

    @Nested
    @DisplayName("detectTags")
    class DetectTags {

        @Test
        @DisplayName("FLAC with application block")
        void withApplicationBlock() throws Exception {
            byte[] streamInfoData = new byte[34];
            int appId = 0x41544348;
            int blockSize = 4 + 20;
            byte[] data = builder()
                    .writeString("fLaC")
                    .writeByte(0x02)
                    .writeByte((blockSize >> 16) & 0xFF)
                    .writeByte((blockSize >> 8) & 0xFF)
                    .writeByte(blockSize & 0xFF)
                    .writeBE32(appId)
                    .writeBytes(20)
                    .build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertEquals(1, tags.size());
            assertEquals(TagFormat.FLAC_APPLICATION, tags.get(0).getFormat());
            assertEquals(4, tags.get(0).getOffset());
            assertEquals(blockSize + 4, tags.get(0).getSize());
        }

        @Test
        @DisplayName("FLAC with no application blocks")
        void noApplicationBlock() throws Exception {
            byte[] data = builder()
                    .writeString("fLaC")
                    .writeByte(0x80 | 0x00)
                    .writeByte(0)
                    .writeByte(0)
                    .writeByte(34)
                    .write(new byte[34])
                    .build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertTrue(tags.isEmpty());
        }

        @Test
        @DisplayName("Application block with blockLength < 4 is skipped")
        void blockLengthTooSmall() throws Exception {
            byte[] streamInfoData = new byte[34];
            byte[] data = builder()
                    .writeString("fLaC")
                    .writeByte(0x80 | 0x00)
                    .writeByte(0)
                    .writeByte(0)
                    .writeByte(34)
                    .write(streamInfoData)
                    .writeByte(0x80 | 0x02)
                    .writeByte(0)
                    .writeByte(0)
                    .writeByte(2)
                    .writeBytes(2)
                    .build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertTrue(tags.isEmpty());
        }
    }

    @Test
    @DisplayName("getSupportedTagFormats")
    void supportedFormats() {
        List<TagFormat> formats = strategy.getSupportedTagFormats();
        assertTrue(formats.contains(TagFormat.FLAC_APPLICATION));
    }
}