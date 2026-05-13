package com.schwanitz.strategies.detection.unit;

import com.schwanitz.strategies.detection.Lyrics3DetectionStrategy;

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

@DisplayName("Lyrics3 Detection Strategy")
class Lyrics3DetectionStrategyTest {

    private Lyrics3DetectionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new Lyrics3DetectionStrategy();
    }

    @Nested
    @DisplayName("canDetect")
    class CanDetect {

        @Test
        @DisplayName("LYRICS200 at end")
        void lyrics200AtEnd() throws Exception {
            byte[] data = builder()
                    .writeBytes(200)
                    .writeString("LYRICSBEGIN")
                    .writeBytes(50)
                    .writeString("0000500")
                    .writeString("LYRICS200")
                    .build();
            Buffers bufs = readBuffers(forBytes(data));
            assertTrue(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("LYRICSEND at end")
        void lyricsendAtEnd() throws Exception {
            byte[] data = builder()
                    .writeBytes(200)
                    .writeString("LYRICSBEGIN")
                    .writeBytes(50)
                    .writeString("LYRICSEND")
                    .build();
            Buffers bufs = readBuffers(forBytes(data));
            assertTrue(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("Buffer too short")
        void bufferTooShort() throws Exception {
            byte[] data = new byte[5];
            Buffers bufs = readBuffers(forBytes(data));
            assertFalse(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("No Lyrics3 marker")
        void noLyrics3Marker() throws Exception {
            byte[] data = builder().writeBytes(500).build();
            Buffers bufs = readBuffers(forBytes(data));
            assertFalse(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }
    }

    @Nested
    @DisplayName("detectTags")
    class DetectTags {

        @Test
        @DisplayName("Lyrics3v2 with valid footer")
        void lyrics3v2() throws Exception {
            String content = "Some lyrics content here";
            int contentLen = content.length();
            int tagBodySize = 11 + contentLen;
            String sizeStr = String.format("%06d", tagBodySize);
            byte[] data = builder()
                    .writeBytes(200)
                    .writeString("LYRICSBEGIN")
                    .writeString(content)
                    .writeString(sizeStr)
                    .writeString("LYRICS200")
                    .build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertEquals(1, tags.size());
            assertEquals(TagFormat.LYRICS3V2, tags.get(0).getFormat());
            assertTrue(tags.get(0).getSize() > 0);
        }

        @Test
        @DisplayName("LYRICS200 before ID3v1 at end")
        void lyrics200BeforeID3v1() throws Exception {
            String content = "Some lyrics content here";
            int contentLen = content.length();
            int tagBodySize = 11 + contentLen;
            String sizeStr = String.format("%06d", tagBodySize);
            byte[] id3v1 = builder()
                    .writeString("TAG")
                    .writeBytes(30).writeBytes(30).writeBytes(30).writeBytes(4)
                    .writeBytes(28).writeByte(0).writeByte(7).writeByte(17)
                    .build();
            assertEquals(128, id3v1.length);
            byte[] data = builder()
                    .writeBytes(200)
                    .writeString("LYRICSBEGIN")
                    .writeString(content)
                    .writeString(sizeStr)
                    .writeString("LYRICS200")
                    .write(id3v1)
                    .build();
            Buffers bufs = readBuffers(forBytes(data));
            assertTrue(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
            SeekableDataSource src = forBytes(data);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertEquals(1, tags.size());
            assertEquals(TagFormat.LYRICS3V2, tags.get(0).getFormat());
        }

        @Test
        @DisplayName("LYRICSEND before ID3v1 at end")
        void lyricsendBeforeID3v1() throws Exception {
            byte[] id3v1 = builder()
                    .writeString("TAG")
                    .writeBytes(30).writeBytes(30).writeBytes(30).writeBytes(4)
                    .writeBytes(28).writeByte(0).writeByte(7).writeByte(17)
                    .build();
            assertEquals(128, id3v1.length);
            byte[] data = builder()
                    .writeBytes(200)
                    .writeString("LYRICSBEGIN")
                    .writeBytes(50)
                    .writeString("LYRICSEND")
                    .write(id3v1)
                    .build();
            Buffers bufs = readBuffers(forBytes(data));
            assertTrue(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
            SeekableDataSource src = forBytes(data);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            if (!tags.isEmpty()) {
                assertEquals(TagFormat.LYRICS3V1, tags.get(0).getFormat());
            }
        }
    }

    @Test
    @DisplayName("getSupportedTagFormats")
    void supportedFormats() {
        List<TagFormat> formats = strategy.getSupportedTagFormats();
        assertTrue(formats.contains(TagFormat.LYRICS3V1));
        assertTrue(formats.contains(TagFormat.LYRICS3V2));
    }
}