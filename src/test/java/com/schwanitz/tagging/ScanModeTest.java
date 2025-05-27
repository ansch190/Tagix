package com.schwanitz.tagging;

import com.schwanitz.others.MetadataManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Scan Mode Tests")
class ScanModeTest {

//    @TempDir
//    Path tempDir;
//
//    @BeforeEach
//    void setUp() {
//        // Cache vor jedem Test leeren
//        TagFormatDetector.clearCache();
//    }
//
//    @Nested
//    @DisplayName("ScanConfiguration Tests")
//    class ScanConfigurationTest {
//
//        @Test
//        @DisplayName("Full Scan Configuration")
//        void testFullScanConfiguration() {
//            ScanConfiguration config = ScanConfiguration.fullScan();
//
//            assertEquals(ScanMode.FULL_SCAN, config.getMode());
//            assertTrue(config.getCustomFormats().isEmpty());
//        }
//
//        @Test
//        @DisplayName("Comfort Scan Configuration")
//        void testComfortScanConfiguration() {
//            ScanConfiguration config = ScanConfiguration.comfortScan();
//
//            assertEquals(ScanMode.COMFORT_SCAN, config.getMode());
//            assertTrue(config.getCustomFormats().isEmpty());
//        }
//
//        @Test
//        @DisplayName("Custom Scan Configuration with Array")
//        void testCustomScanConfigurationArray() {
//            TagFormat[] formats = {TagFormat.ID3V2_3, TagFormat.VORBIS_COMMENT, TagFormat.MP4};
//            ScanConfiguration config = ScanConfiguration.customScan(formats);
//
//            assertEquals(ScanMode.CUSTOM_SCAN, config.getMode());
//            assertEquals(3, config.getCustomFormats().size());
//            assertTrue(config.getCustomFormats().contains(TagFormat.ID3V2_3));
//            assertTrue(config.getCustomFormats().contains(TagFormat.VORBIS_COMMENT));
//            assertTrue(config.getCustomFormats().contains(TagFormat.MP4));
//        }
//
//        @Test
//        @DisplayName("Custom Scan Configuration with List")
//        void testCustomScanConfigurationList() {
//            List<TagFormat> formats = Arrays.asList(TagFormat.ID3V1, TagFormat.APEV2);
//            ScanConfiguration config = ScanConfiguration.customScan(formats);
//
//            assertEquals(ScanMode.CUSTOM_SCAN, config.getMode());
//            assertEquals(2, config.getCustomFormats().size());
//            assertTrue(config.getCustomFormats().contains(TagFormat.ID3V1));
//            assertTrue(config.getCustomFormats().contains(TagFormat.APEV2));
//        }
//
//        @Test
//        @DisplayName("Custom Scan with Empty Array throws Exception")
//        void testCustomScanEmptyArray() {
//            assertThrows(IllegalArgumentException.class, () -> {
//                ScanConfiguration.customScan();
//            });
//        }
//
//        @Test
//        @DisplayName("Custom Scan with Null Array throws Exception")
//        void testCustomScanNullArray() {
//            TagFormat[] formats = null;
//            assertThrows(IllegalArgumentException.class, () -> {
//                ScanConfiguration.customScan(formats);
//            });
//        }
//
//        @Test
//        @DisplayName("Custom Scan with Empty List throws Exception")
//        void testCustomScanEmptyList() {
//            List<TagFormat> formats = Arrays.asList();
//            assertThrows(IllegalArgumentException.class, () -> {
//                ScanConfiguration.customScan(formats);
//            });
//        }
//
//        @Test
//        @DisplayName("Custom Scan with Null List throws Exception")
//        void testCustomScanNullList() {
//            List<TagFormat> formats = null;
//            assertThrows(IllegalArgumentException.class, () -> {
//                ScanConfiguration.customScan(formats);
//            });
//        }
//
//        @Test
//        @DisplayName("Configuration Immutability")
//        void testConfigurationImmutability() {
//            List<TagFormat> originalFormats = Arrays.asList(TagFormat.ID3V2_3, TagFormat.MP4);
//            ScanConfiguration config = ScanConfiguration.customScan(originalFormats);
//
//            // Versuche die originale Liste zu ändern
//            originalFormats.clear();
//
//            // Configuration sollte unverändert bleiben
//            assertEquals(2, config.getCustomFormats().size());
//
//            // Versuche die Configuration-Liste zu ändern
//            assertThrows(UnsupportedOperationException.class, () -> {
//                config.getCustomFormats().clear();
//            });
//        }
//    }
//
//    @Nested
//    @DisplayName("FormatPriorityManager Tests")
//    class FormatPriorityManagerTest {
//
//        @Test
//        @DisplayName("Full Scan Priority List")
//        void testFullScanPriority() {
//            List<TagFormat> priority = FormatPriorityManager.getFullScanPriority();
//
//            assertNotNull(priority);
//            assertFalse(priority.isEmpty());
//
//            // ID3V2_3 sollte höchste Priorität haben (häufigstes Format)
//            assertEquals(TagFormat.ID3V2_3, priority.get(0));
//
//            // Alle wichtigen Formate sollten enthalten sein
//            assertTrue(priority.contains(TagFormat.ID3V2_4));
//            assertTrue(priority.contains(TagFormat.VORBIS_COMMENT));
//            assertTrue(priority.contains(TagFormat.MP4));
//            assertTrue(priority.contains(TagFormat.APEV2));
//        }
//
//        @ParameterizedTest
//        @ValueSource(strings = {"mp3", "wav", "ogg", "flac", "m4a", "aiff"})
//        @DisplayName("Comfort Scan Priority for Supported Extensions")
//        void testComfortScanPrioritySupported(String extension) {
//            List<TagFormat> priority = FormatPriorityManager.getComfortScanPriority(extension);
//
//            assertNotNull(priority);
//            assertFalse(priority.isEmpty());
//
//            // Für unterstützte Endungen sollte eine spezifische Prioritätsliste zurückgegeben werden
//            assertTrue(FormatPriorityManager.isExtensionSupported(extension));
//        }
//
//        @Test
//        @DisplayName("Comfort Scan Priority for Unknown Extension")
//        void testComfortScanPriorityUnknown() {
//            List<TagFormat> priority = FormatPriorityManager.getComfortScanPriority("unknown");
//            List<TagFormat> fullScanPriority = FormatPriorityManager.getFullScanPriority();
//
//            // Für unbekannte Endungen sollte Full Scan Priorität zurückgegeben werden
//            assertEquals(fullScanPriority, priority);
//        }
//
//        @Test
//        @DisplayName("MP3 Priority Order")
//        void testMP3PriorityOrder() {
//            List<TagFormat> mp3Priority = FormatPriorityManager.getComfortScanPriority("mp3");
//
//            // ID3 Formate sollten höchste Priorität bei MP3 haben
//            assertEquals(TagFormat.ID3V2_3, mp3Priority.get(0));
//            assertEquals(TagFormat.ID3V2_4, mp3Priority.get(1));
//
//            // APE sollte nach ID3 kommen
//            assertTrue(mp3Priority.indexOf(TagFormat.APEV2) > mp3Priority.indexOf(TagFormat.ID3V1));
//        }
//
//        @Test
//        @DisplayName("OGG Priority - Only Vorbis Comment")
//        void testOGGPriority() {
//            List<TagFormat> oggPriority = FormatPriorityManager.getComfortScanPriority("ogg");
//
//            assertEquals(1, oggPriority.size());
//            assertEquals(TagFormat.VORBIS_COMMENT, oggPriority.get(0));
//        }
//
//        @Test
//        @DisplayName("Add and Remove Extension Priority")
//        void testAddRemoveExtensionPriority() {
//            String testExtension = "test";
//            List<TagFormat> testPriority = Arrays.asList(TagFormat.ID3V2_4, TagFormat.MP4);
//
//            // Vor dem Hinzufügen sollte die Endung nicht unterstützt werden
//            assertFalse(FormatPriorityManager.isExtensionSupported(testExtension));
//
//            // Priorität hinzufügen
//            FormatPriorityManager.addExtensionPriority(testExtension, testPriority);
//            assertTrue(FormatPriorityManager.isExtensionSupported(testExtension));
//
//            List<TagFormat> retrievedPriority = FormatPriorityManager.getComfortScanPriority(testExtension);
//            assertEquals(testPriority, retrievedPriority);
//
//            // Priorität entfernen
//            FormatPriorityManager.removeExtensionPriority(testExtension);
//            assertFalse(FormatPriorityManager.isExtensionSupported(testExtension));
//        }
//
//        @Test
//        @DisplayName("Supported Extensions List")
//        void testSupportedExtensions() {
//            List<String> supportedExtensions = FormatPriorityManager.getSupportedExtensions();
//
//            assertNotNull(supportedExtensions);
//            assertFalse(supportedExtensions.isEmpty());
//
//            // Wichtige Endungen sollten unterstützt werden
//            assertTrue(supportedExtensions.contains("mp3"));
//            assertTrue(supportedExtensions.contains("wav"));
//            assertTrue(supportedExtensions.contains("ogg"));
//            assertTrue(supportedExtensions.contains("flac"));
//            assertTrue(supportedExtensions.contains("m4a"));
//        }
//    }
//
//    @Nested
//    @DisplayName("TagFormatDetector Integration Tests")
//    class TagFormatDetectorIntegrationTest {
//
//        @Test
//        @DisplayName("Detect with Full Scan Configuration")
//        void testDetectWithFullScan() throws IOException {
//            File testFile = createTestMP3File();
//
//            List<TagInfo> tags = TagFormatDetector.customScan(
//                    testFile.getAbsolutePath(), ScanConfiguration.fullScan());
//
//            assertNotNull(tags);
//            // Bei einem echten Test würden hier die erwarteten Tags geprüft
//        }
//
//        @Test
//        @DisplayName("Detect with Comfort Scan Configuration")
//        void testDetectWithComfortScan() throws IOException {
//            File testFile = createTestMP3File();
//
//            List<TagInfo> tags = TagFormatDetector.customScan(
//                    testFile.getAbsolutePath(), ScanConfiguration.comfortScan());
//
//            assertNotNull(tags);
//        }
//
//        @Test
//        @DisplayName("Detect with Custom Scan Configuration")
//        void testDetectWithCustomScan() throws IOException {
//            File testFile = createTestMP3File();
//
//            ScanConfiguration config = ScanConfiguration.customScan(TagFormat.ID3V2_3, TagFormat.ID3V1);
//            List<TagInfo> tags = TagFormatDetector.customScan(testFile.getAbsolutePath(), config);
//
//            assertNotNull(tags);
//        }
//
//        @Test
//        @DisplayName("Convenience Methods")
//        void testConvenienceMethods() throws IOException {
//            File testFile = createTestMP3File();
//            String filePath = testFile.getAbsolutePath();
//
//            // Full Scan Convenience
//            List<TagInfo> fullScanTags = TagFormatDetector.fullScan(filePath);
//            assertNotNull(fullScanTags);
//
//            // Comfort Scan Convenience
//            List<TagInfo> comfortScanTags = TagFormatDetector.comfortScan(filePath);
//            assertNotNull(comfortScanTags);
//        }
//
//        @Test
//        @DisplayName("Batch Processing")
//        void testBatchProcessing() throws IOException {
//            File testFile1 = createTestMP3File();
//            File testFile2 = createTestWAVFile();
//
//            List<String> filePaths = Arrays.asList(
//                    testFile1.getAbsolutePath(),
//                    testFile2.getAbsolutePath()
//            );
//
//            Map<String, List<TagInfo>> results = TagFormatDetector.customScan(
//                    filePaths, ScanConfiguration.comfortScan());
//
//            assertEquals(2, results.size());
//            assertTrue(results.containsKey(testFile1.getAbsolutePath()));
//            assertTrue(results.containsKey(testFile2.getAbsolutePath()));
//        }
//
//        @Test
//        @DisplayName("Cache Functionality")
//        void testCacheFunctionality() throws IOException {
//            File testFile = createTestMP3File();
//            String filePath = testFile.getAbsolutePath();
//            ScanConfiguration config = ScanConfiguration.comfortScan();
//
//            // Erster Aufruf
//            List<TagInfo> firstCall = TagFormatDetector.customScan(filePath, config);
//
//            // Zweiter Aufruf sollte aus Cache kommen
//            List<TagInfo> secondCall = TagFormatDetector.customScan(filePath, config);
//
//            assertEquals(firstCall.size(), secondCall.size());
//
//            // Cache-Statistiken prüfen
//            assertTrue(TagFormatDetector.getCacheSize() > 0);
//
//            // Cache leeren
//            TagFormatDetector.clearCache();
//            assertEquals(0, TagFormatDetector.getCacheSize());
//
//            // Nach Cache-Leerung sollte wieder gescannt werden
//            List<TagInfo> thirdCall = TagFormatDetector.customScan(filePath, config);
//            assertNotNull(thirdCall);
//        }
//
//        @Test
//        @DisplayName("Cache Entry Removal")
//        void testCacheEntryRemoval() throws IOException {
//            File testFile = createTestMP3File();
//            String filePath = testFile.getAbsolutePath();
//
//            // Cache füllen
//            TagFormatDetector.customScan(filePath, ScanConfiguration.comfortScan());
//            TagFormatDetector.customScan(filePath, ScanConfiguration.fullScan());
//
//            int initialCacheSize = TagFormatDetector.getCacheSize();
//            assertTrue(initialCacheSize >= 2);
//
//            // Einzelnen Eintrag entfernen
//            TagFormatDetector.removeCacheEntry(filePath);
//
//            int finalCacheSize = TagFormatDetector.getCacheSize();
//            assertTrue(finalCacheSize < initialCacheSize);
//        }
//    }
//
//    @Nested
//    @DisplayName("MetadataManager Integration Tests")
//    class MetadataManagerIntegrationTest {
//
//        @Test
//        @DisplayName("MetadataManager with Different Scan Modes")
//        void testMetadataManagerScanModes() throws IOException {
//            File testFile = createTestMP3File();
//            String filePath = testFile.getAbsolutePath();
//
//            MetadataManager manager = new MetadataManager();
//
//            // Comfort Scan (Standard)
//            manager.readFromFile(filePath);
//            int comfortScanCount = manager.getMetadataCount();
//
//            // Full Scan
//            manager.clearMetadata();
//            manager.readFromFileFullScan(filePath);
//            int fullScanCount = manager.getMetadataCount();
//
//            // Custom Scan
//            manager.clearMetadata();
//            manager.readFromFileCustomScan(filePath, TagFormat.ID3V2_3, TagFormat.ID3V1);
//            int customScanCount = manager.getMetadataCount();
//
//            // Alle Scan-Modi sollten Ergebnisse liefern
//            assertTrue(comfortScanCount >= 0);
//            assertTrue(fullScanCount >= 0);
//            assertTrue(customScanCount >= 0);
//        }
//
//        @Test
//        @DisplayName("MetadataManager Batch Processing")
//        void testMetadataManagerBatchProcessing() throws IOException {
//            File testFile1 = createTestMP3File();
//            File testFile2 = createTestWAVFile();
//
//            List<String> filePaths = Arrays.asList(
//                    testFile1.getAbsolutePath(),
//                    testFile2.getAbsolutePath()
//            );
//
//            MetadataManager manager = new MetadataManager();
//            Map<String, Integer> results = manager.readFromFiles(filePaths, ScanConfiguration.comfortScan());
//
//            assertEquals(2, results.size());
//            assertTrue(results.containsKey(testFile1.getAbsolutePath()));
//            assertTrue(results.containsKey(testFile2.getAbsolutePath()));
//        }
//
//        @Test
//        @DisplayName("MetadataManager Backward Compatibility")
//        void testMetadataManagerBackwardCompatibility() throws IOException {
//            File testFile = createTestMP3File();
//            String filePath = testFile.getAbsolutePath();
//
//            MetadataManager manager = new MetadataManager();
//
//            // Alte Methode sollte noch funktionieren
//            manager.readFromFile(filePath, true); // fullSearch = true
//            int oldMethodCount = manager.getMetadataCount();
//
//            manager.clearMetadata();
//            manager.readFromFileFullScan(filePath);
//            int newMethodCount = manager.getMetadataCount();
//
//            assertEquals(oldMethodCount, newMethodCount);
//        }
//    }
//
//    @Nested
//    @DisplayName("Error Handling Tests")
//    class ErrorHandlingTest {
//
//        @Test
//        @DisplayName("Non-existent File")
//        void testNonExistentFile() {
//            String nonExistentFile = tempDir.resolve("does_not_exist.mp3").toString();
//
//            assertThrows(IOException.class, () -> {
//                TagFormatDetector.customScan(nonExistentFile, ScanConfiguration.comfortScan());
//            });
//        }
//
//        @Test
//        @DisplayName("Invalid File Path")
//        void testInvalidFilePath() {
//            assertThrows(IOException.class, () -> {
//                TagFormatDetector.customScan("", ScanConfiguration.comfortScan());
//            });
//        }
//
//        @Test
//        @DisplayName("Corrupted File Handling")
//        void testCorruptedFileHandling() throws IOException {
//            File corruptedFile = createCorruptedFile();
//
//            // Sollte nicht crashen, auch wenn die Datei korrupt ist
//            assertDoesNotThrow(() -> {
//                List<TagInfo> tags = TagFormatDetector.customScan(
//                        corruptedFile.getAbsolutePath(), ScanConfiguration.comfortScan());
//                assertNotNull(tags);
//            });
//        }
//    }
//
//    // Helper Methods
//
//    private File createTestMP3File() throws IOException {
//        File file = new File(tempDir.toFile(), "test.mp3");
//        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
//            // Minimaler MP3 Header
//            raf.write(new byte[]{(byte)0xFF, (byte)0xFB, (byte) 0x90, 0x00}); // MP3 Sync + Header
//            raf.write(new byte[1000]); // Dummy Audio Data
//        }
//        return file;
//    }
//
//    private File createTestWAVFile() throws IOException {
//        File file = new File(tempDir.toFile(), "test.wav");
//        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
//            // RIFF Header
//            raf.write("RIFF".getBytes());
//            raf.writeInt(Integer.reverseBytes(1036)); // File size - 8
//            raf.write("WAVE".getBytes());
//
//            // Format Chunk
//            raf.write("fmt ".getBytes());
//            raf.writeInt(Integer.reverseBytes(16)); // Chunk size
//            raf.writeShort(Short.reverseBytes((short)1)); // PCM
//            raf.writeShort(Short.reverseBytes((short)2)); // Stereo
//            raf.writeInt(Integer.reverseBytes(44100)); // Sample rate
//            raf.writeInt(Integer.reverseBytes(176400)); // Byte rate
//            raf.writeShort(Short.reverseBytes((short)4)); // Block align
//            raf.writeShort(Short.reverseBytes((short)16)); // Bits per sample
//
//            // Data Chunk
//            raf.write("data".getBytes());
//            raf.writeInt(Integer.reverseBytes(1000)); // Data size
//            raf.write(new byte[1000]); // Dummy audio data
//        }
//        return file;
//    }
//
//    private File createCorruptedFile() throws IOException {
//        File file = new File(tempDir.toFile(), "corrupted.mp3");
//        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
//            // Zufällige Bytes ohne gültige Header
//            raf.write(new byte[]{0x12, 0x34, 0x56, 0x78, (byte)0x9A, (byte)0xBC, (byte)0xDE, (byte)0xF0});
//            raf.write(new byte[100]); // Mehr zufällige Daten
//        }
//        return file;
//    }
}