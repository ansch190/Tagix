package com.schwanitz.strategies.detection.unit;

import com.schwanitz.strategies.detection.DSDDetectionStrategy;

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

@DisplayName("DSD Detection Strategy")
class DSDDetectionStrategyTest {

    private DSDDetectionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new DSDDetectionStrategy();
    }

    @Nested
    @DisplayName("canDetect")
    class CanDetect {

        @Test
        @DisplayName("DSF signature 'DSD '")
        void dsfSignature() throws Exception {
            byte[] data = builder().writeString("DSD ").writeBytes(200).build();
            Buffers bufs = readBuffers(forBytes(data));
            assertTrue(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

@Test
        @DisplayName("DFF signature 'FRM8'+'DSD '")
        void dffSignature() throws Exception {
            byte[] data = builder()
                    .writeString("FRM8")
                    .write(be64(100))
                    .writeString("DSD ")
                    .writeBytes(84)
                    .build();
            Buffers bufs = readBuffers(forBytes(data));
            assertTrue(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("FRM8 without DSD subtype")
        void frm8WithoutDsd() throws Exception {
            byte[] data = builder()
                    .writeString("FRM8")
                    .write(be64(100))
                    .writeString("AIFF")
                    .writeBytes(84)
                    .build();
            Buffers bufs = readBuffers(forBytes(data));
            assertFalse(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("Invalid signature")
        void invalidSignature() throws Exception {
            byte[] data = builder().writeString("RIFF").writeBytes(200).build();
            Buffers bufs = readBuffers(forBytes(data));
            assertFalse(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("Buffer too short for DSF")
        void bufferTooShortDsf() throws Exception {
            byte[] data = new byte[5];
            Buffers bufs = readBuffers(forBytes(data));
            assertFalse(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }
    }

    @Nested
    @DisplayName("detectTags DSF")
    class DetectTagsDSF {

        @Test
        @DisplayName("DSF with ID3 metadata chunk at non-zero offset")
        void dsfWithId3Metadata() throws Exception {
            long metadataPointer = 28;
            int id3ChunkSize = 50;
            byte[] id3Chunk = builder()
                    .writeString("ID3 ")
                    .write(le64(id3ChunkSize))
                    .writeBytes(id3ChunkSize)
                    .build();
            byte[] data = builder()
                    .writeString("DSD ")
                    .writeBytes(16)
                    .write(le64(metadataPointer))
                    .writeBytes((int) (metadataPointer - 28))
                    .write(id3Chunk)
                    .build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            assertTrue(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertEquals(1, tags.size());
            assertEquals(TagFormat.DSF_METADATA, tags.get(0).getFormat());
            assertEquals(metadataPointer, tags.get(0).getOffset());
        }

        @Test
        @DisplayName("DSF with metadata pointer = 0")
        void dsfNoMetadata() throws Exception {
            byte[] data = builder()
                    .writeString("DSD ")
                    .writeBytes(16)
                    .write(le64(0))
                    .writeBytes(76)
                    .build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertTrue(tags.isEmpty());
        }
    }

    @Nested
    @DisplayName("detectTags DFF")
    class DetectTagsDFF {

@Test
        @DisplayName("DFF with DIIN chunk")
        void dffWithDiin() throws Exception {
            int chunkSize = 20;
            // DFF layout: "FRM8"(4) + formSize(8) + "DSD "(4) = 16 byte header
            // Then: DIIN(4) + chunkSize(8) + data(chunkSize)
            long formSize = 4 + 4 + 8 + chunkSize;
            byte[] data = builder()
                    .writeString("FRM8")
                    .write(be64(formSize))
                    .writeString("DSD ")
                    .writeString("DIIN")
                    .write(be64(chunkSize))
                    .writeBytes(chunkSize)
                    .build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            assertTrue(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertFalse(tags.isEmpty());
            assertEquals(TagFormat.DFF_METADATA, tags.get(0).getFormat());
        }

        @Test
        @DisplayName("DFF with no metadata chunks")
        void dffNoMetadata() throws Exception {
            long formSize = 4 + 4 + 8 + 100;
            byte[] data = builder()
                    .writeString("FRM8")
                    .write(be64(formSize))
                    .writeString("DSD ")
                    .writeString("FVER")
                    .write(be64(100))
                    .writeBytes(100)
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
        assertTrue(formats.contains(TagFormat.DSF_METADATA));
        assertTrue(formats.contains(TagFormat.DFF_METADATA));
    }
}