package com.schwanitz.strategies.detection;

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

@DisplayName("AIFF Detection Strategy")
class AIFFDetectionStrategyTest {

    private AIFFDetectionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new AIFFDetectionStrategy();
    }

    private byte[] aiffFile(String chunkType, int chunkSize) {
        int totalSize = 4 + 8 + chunkSize;
        return builder()
                .writeString("FORM")
                .write(be32(totalSize))
                .writeString("AIFF")
                .writeString(chunkType)
                .write(be32(chunkSize))
                .writeBytes(chunkSize)
                .build();
    }

    @Nested
    @DisplayName("canDetect")
    class CanDetect {

        @Test
        @DisplayName("FORM AIFF header")
        void formAiff() throws Exception {
            byte[] data = aiffFile("NAME", 10);
            Buffers bufs = readBuffers(forBytes(data));
            assertTrue(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("FORM AIFC header")
        void formAifc() throws Exception {
            byte[] data = builder()
                    .writeString("FORM").write(be32(4)).writeString("AIFC")
                    .build();
            Buffers bufs = readBuffers(forBytes(data));
            assertTrue(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("FORM without AIFF/AIFC")
        void formWithoutAiff() throws Exception {
            byte[] data = builder().writeString("FORM").write(be32(4)).writeString("RIFF").build();
            Buffers bufs = readBuffers(forBytes(data));
            assertFalse(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("Buffer too short")
        void bufferTooShort() throws Exception {
            byte[] data = new byte[8];
            Buffers bufs = readBuffers(forBytes(data));
            assertFalse(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }
    }

    @Nested
    @DisplayName("detectTags")
    class DetectTags {

        @Test
        @DisplayName("AIFF with NAME chunk")
        void nameChunk() throws Exception {
            int nameDataLen = 20;
            byte[] data = builder()
                    .writeString("FORM")
                    .write(be32(4 + 8 + nameDataLen))
                    .writeString("AIFF")
                    .writeString("NAME")
                    .write(be32(nameDataLen))
                    .writeBytes(nameDataLen)
                    .build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertEquals(1, tags.size());
            assertEquals(TagFormat.AIFF_METADATA, tags.get(0).getFormat());
            assertEquals(12, tags.get(0).getOffset());
            assertEquals(nameDataLen + 8, tags.get(0).getSize());
        }

        @Test
        @DisplayName("AIFF with (c) chunk")
        void copyrightChunk() throws Exception {
            int dataLen = 30;
            byte[] fileData = builder()
                    .writeString("FORM")
                    .write(be32(4 + 8 + dataLen))
                    .writeString("AIFF")
                    .writeString("(c) ")
                    .write(be32(dataLen))
                    .writeBytes(dataLen)
                    .build();
            SeekableDataSource src = forBytes(fileData);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertEquals(1, tags.size());
            assertEquals(TagFormat.AIFF_METADATA, tags.get(0).getFormat());
        }

        @Test
        @DisplayName("AIFF with no metadata chunks")
        void noMetadata() throws Exception {
            byte[] data = builder()
                    .writeString("FORM")
                    .write(be32(4 + 8 + 100))
                    .writeString("AIFF")
                    .writeString("SSND")
                    .write(be32(100))
                    .writeBytes(100)
                    .build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertTrue(tags.isEmpty());
        }

        @Test
        @DisplayName("chunkSize 0 stops iteration")
        void chunkSizeZero() throws Exception {
            byte[] data = builder()
                    .writeString("FORM")
                    .write(be32(4 + 8))
                    .writeString("AIFF")
                    .writeString("TEST")
                    .write(be32(0))
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
        assertTrue(formats.contains(TagFormat.AIFF_METADATA));
    }
}