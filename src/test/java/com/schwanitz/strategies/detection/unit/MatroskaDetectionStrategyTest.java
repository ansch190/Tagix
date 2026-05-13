package com.schwanitz.strategies.detection.unit;

import com.schwanitz.strategies.detection.MatroskaDetectionStrategy;

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

@DisplayName("Matroska Detection Strategy")
class MatroskaDetectionStrategyTest {

    private MatroskaDetectionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new MatroskaDetectionStrategy();
    }

    private byte[] vli(long value) {
        if (value < 0x80) {
            return new byte[]{(byte) (value | 0x80)};
        } else if (value < 0x4000) {
            return new byte[]{(byte) ((value >> 8) | 0x40), (byte) (value & 0xFF)};
        } else if (value < 0x200000) {
            return new byte[]{
                    (byte) ((value >> 16) | 0x20),
                    (byte) ((value >> 8) & 0xFF),
                    (byte) (value & 0xFF)
            };
        } else {
            return new byte[]{
                    (byte) ((value >> 24) | 0x10),
                    (byte) ((value >> 16) & 0xFF),
                    (byte) ((value >> 8) & 0xFF),
                    (byte) (value & 0xFF)
            };
        }
    }

    @Nested
    @DisplayName("canDetect")
    class CanDetect {

        @Test
        @DisplayName("EBML header signature")
        void ebmlHeader() throws Exception {
            byte[] data = builder()
                    .write(new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3})
                    .writeBytes(200)
                    .build();
            Buffers bufs = readBuffers(forBytes(data));
            assertTrue(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("Invalid signature")
        void invalidSignature() throws Exception {
            byte[] data = builder().writeBytes(200).build();
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

    @Test
    @DisplayName("getSupportedTagFormats")
    void supportedFormats() {
        List<TagFormat> formats = strategy.getSupportedTagFormats();
        assertTrue(formats.contains(TagFormat.MATROSKA_TAGS));
        assertTrue(formats.contains(TagFormat.WEBM_TAGS));
    }
}