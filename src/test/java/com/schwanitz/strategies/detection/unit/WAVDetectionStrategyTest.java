package com.schwanitz.strategies.detection.unit;

import com.schwanitz.strategies.detection.WAVDetectionStrategy;

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

@DisplayName("WAV Detection Strategy")
class WAVDetectionStrategyTest {

    private WAVDetectionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new WAVDetectionStrategy();
    }

    private byte[] wavFile(String... chunks) {
        Builder b = builder();
        int totalSize = 4;
        for (int i = 0; i < chunks.length; i += 3) {
            totalSize += 8 + Integer.parseInt(chunks[i + 2]);
        }
        b.writeString("RIFF");
        b.write(le32(totalSize));
        b.writeString("WAVE");
        for (int i = 0; i < chunks.length; i += 3) {
            String type = chunks[i];
            int size = Integer.parseInt(chunks[i + 2]);
            byte[] chunkData = chunks[i + 1] != null ? new byte[size] : new byte[size];
            b.writeString(type);
            b.write(le32(size));
            b.write(chunkData);
        }
        return b.build();
    }

    @Nested
    @DisplayName("canDetect")
    class CanDetect {

        @Test
        @DisplayName("RIFF WAVE header")
        void riffWave() throws Exception {
            byte[] data = builder()
                    .writeString("RIFF").write(le32(100)).writeString("WAVE")
                    .writeBytes(100)
                    .build();
            Buffers bufs = readBuffers(forBytes(data));
            assertTrue(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("RIFF without WAVE")
        void riffWithoutWave() throws Exception {
            byte[] data = builder()
                    .writeString("RIFF").write(le32(100)).writeString("AVI ")
                    .writeBytes(100)
                    .build();
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
        @DisplayName("WAV with LIST INFO chunk")
        void listInfoChunk() throws Exception {
            byte[] data = builder()
                    .writeString("RIFF")
                    .write(le32(4 + 8 + 8 + 4))
                    .writeString("WAVE")
                    .writeString("LIST")
                    .write(le32(8 + 4))
                    .writeString("INFO")
                    .writeString("INAM")
                    .write(le32(0))
                    .build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertEquals(1, tags.size());
            assertEquals(TagFormat.RIFF_INFO, tags.get(0).getFormat());
        }

        @Test
        @DisplayName("WAV with bext chunk v1")
        void bextV1() throws Exception {
            byte[] bextData = new byte[602 + 2 + 100];
            bextData[602] = 1;
            bextData[603] = 0;
            byte[] data = builder()
                    .writeString("RIFF")
                    .write(le32(4 + 8 + bextData.length))
                    .writeString("WAVE")
                    .writeString("bext")
                    .write(le32(bextData.length))
                    .write(bextData)
                    .build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertEquals(1, tags.size());
            assertEquals(TagFormat.BWF_V1, tags.get(0).getFormat());
        }

        @Test
        @DisplayName("WAV with bext chunk v2")
        void bextV2() throws Exception {
            byte[] bextData = new byte[602 + 2 + 100];
            bextData[602] = 2;
            bextData[603] = 0;
            byte[] data = builder()
                    .writeString("RIFF")
                    .write(le32(4 + 8 + bextData.length))
                    .writeString("WAVE")
                    .writeString("bext")
                    .write(le32(bextData.length))
                    .write(bextData)
                    .build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertEquals(1, tags.size());
            assertEquals(TagFormat.BWF_V2, tags.get(0).getFormat());
        }

        @Test
        @DisplayName("bext chunk too small is skipped")
        void bextTooSmall() throws Exception {
            byte[] data = builder()
                    .writeString("RIFF")
                    .write(le32(4 + 8 + 100))
                    .writeString("WAVE")
                    .writeString("bext")
                    .write(le32(100))
                    .writeBytes(100)
                    .build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertTrue(tags.isEmpty());
        }

        @Test
        @DisplayName("WAV with no metadata chunks")
        void noMetadata() throws Exception {
            byte[] data = builder()
                    .writeString("RIFF")
                    .write(le32(4 + 8 + 16))
                    .writeString("WAVE")
                    .writeString("fmt ")
                    .write(le32(16))
                    .writeBytes(16)
                    .build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertTrue(tags.isEmpty());
        }

        @Test
        @DisplayName("LIST chunk with non-INFO type")
        void listNonInfo() throws Exception {
            byte[] data = builder()
                    .writeString("RIFF")
                    .write(le32(4 + 8 + 8 + 4))
                    .writeString("WAVE")
                    .writeString("LIST")
                    .write(le32(8 + 4))
                    .writeString("adtl")
                    .writeString("labl")
                    .write(le32(0))
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
        assertTrue(formats.contains(TagFormat.RIFF_INFO));
        assertTrue(formats.contains(TagFormat.BWF_V0));
        assertTrue(formats.contains(TagFormat.BWF_V1));
        assertTrue(formats.contains(TagFormat.BWF_V2));
    }
}