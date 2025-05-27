package com.schwanitz.performance;

import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagFormatDetector;
import com.schwanitz.tagging.TagInfo;
import com.schwanitz.testutils.Mp3FileGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
@Tag("vorbis")
@Tag("parsing")
public class PerformanceTests {

    @Test
    @DisplayName("Test FullScan of 10.000 MP3 File without Tags")
    void MP3NoTags10kFilesFullScanTest() throws IOException {
        String path = "src/test/resources/testfiles/mp3/";

        //Verzeichnis leeren
        //Arrays.stream(Objects.requireNonNull(new File(path).listFiles())).forEach(File::delete);

        //Create Files
        for (int i=0; i<10; i++){
          //Mp3FileGenerator.generate1000TestFiles();
        }

        //Count of Files
        File f = new File(path);
        int fileCount = Objects.requireNonNull(f.listFiles()).length;
        assertEquals(10000, fileCount);

        long startTime = System.currentTimeMillis();

        //Scan Files (Each File separated)
        List<File> fileList = List.of(Objects.requireNonNull(f.listFiles()));
        Map<String,List<TagInfo>> results = new HashMap<>();
        for (File file : fileList) {
            results.put(file.getAbsolutePath(), TagFormatDetector.fullScan(file.getAbsolutePath()));
        }

        long simpleResultTime = System.currentTimeMillis() - startTime;
        startTime = System.currentTimeMillis();

        //Scan Files (Files as List)
        List<String> files = Arrays.stream(Objects.requireNonNull(f.listFiles()))
                .map(File::getAbsolutePath)
                .toList();
        System.out.println("FilesCount: " + files.size());
        results = TagFormatDetector.fullScan(files);

        long listResultTime = System.currentTimeMillis() - startTime;

        //Verzeichnis leeren
        //Arrays.stream(Objects.requireNonNull(new File(path).listFiles())).forEach(File::delete);

        System.out.println("======== Peformance Test ========");
        System.out.println("FileCount: 10.000 MP3 (5MB) without Tags");
        System.out.println("SimpleTime: " + simpleResultTime);  //Result: 1,734 Sec.
        System.out.println("ListTime: " + listResultTime);  //Result: 0,327 Sec.
        System.out.printf("Speedup: %.2fx faster%n", (double) simpleResultTime / listResultTime);  //Result: 5,3x faster
    }

    @Test
    @DisplayName("Test ComfortScan of 10.000 MP3 File without Tags")
    void MP3NoTags10kFilesComfortScanTest() throws IOException {
        String path = "src/test/resources/testfiles/mp3/";

        //Verzeichnis leeren
        //Arrays.stream(Objects.requireNonNull(new File(path).listFiles())).forEach(File::delete);

        //Create Files
        for (int i=0; i<10; i++){
            //Mp3FileGenerator.generate1000TestFiles();
        }

        //Count of Files
        File f = new File(path);
        int fileCount = Objects.requireNonNull(f.listFiles()).length;
        assertEquals(10000, fileCount);

        long startTime = System.currentTimeMillis();

        //Scan Files (Each File separated)
        List<File> fileList = List.of(Objects.requireNonNull(f.listFiles()));
        Map<String,List<TagInfo>> results = new HashMap<>();
        for (File file : fileList) {
            results.put(file.getAbsolutePath(), TagFormatDetector.comfortScan(file.getAbsolutePath()));
        }

        long simpleResultTime = System.currentTimeMillis() - startTime;
        startTime = System.currentTimeMillis();

        //Scan Files (Files as List)
        List<String> files = Arrays.stream(Objects.requireNonNull(f.listFiles()))
                .map(File::getAbsolutePath)
                .toList();
        System.out.println("FilesCount: " + files.size());
        results = TagFormatDetector.comfortScan(files);

        long listResultTime = System.currentTimeMillis() - startTime;

        //Verzeichnis leeren
        //Arrays.stream(Objects.requireNonNull(new File(path).listFiles())).forEach(File::delete);

        System.out.println("======== Peformance Test ========");
        System.out.println("FileCount: 10.000 MP3 (5MB) without Tags");
        System.out.println("SimpleTime: " + simpleResultTime);  //Result: 0,515 Sec.
        System.out.println("ListTime: " + listResultTime);  //Result: 0,355 Sec.
        System.out.printf("Speedup: %.2fx faster%n", (double) simpleResultTime / listResultTime);  //Result: 1,45x faster
    }

    @Test
    @DisplayName("Test CustomScan of 10.000 MP3 File without Tags for ID32.3")
    void MP3NoTags10kFilesCustomScanID3v2_3Test() throws IOException {
        String path = "src/test/resources/testfiles/mp3/";

        //Verzeichnis leeren
        //Arrays.stream(Objects.requireNonNull(new File(path).listFiles())).forEach(File::delete);

        //Create Files
        for (int i=0; i<10; i++){
            //Mp3FileGenerator.generate1000TestFiles();
        }

        //Count of Files
        File f = new File(path);
        int fileCount = Objects.requireNonNull(f.listFiles()).length;
        assertEquals(10000, fileCount);

        long startTime = System.currentTimeMillis();

        //Scan Files (Each File separated)
        List<File> fileList = List.of(Objects.requireNonNull(f.listFiles()));
        Map<String,List<TagInfo>> results = new HashMap<>();
        for (File file : fileList) {
            results.put(file.getAbsolutePath(), TagFormatDetector.customScan(file.getAbsolutePath(), TagFormat.ID3V2_3));
        }

        long simpleResultTime = System.currentTimeMillis() - startTime;
        startTime = System.currentTimeMillis();

        //Scan Files (Files as List)
        List<String> files = Arrays.stream(Objects.requireNonNull(f.listFiles()))
                .map(File::getAbsolutePath)
                .toList();
        System.out.println("FilesCount: " + files.size());
        results = TagFormatDetector.customScan(files,TagFormat.ID3V2_3);

        long listResultTime = System.currentTimeMillis() - startTime;

        //Verzeichnis leeren
        //Arrays.stream(Objects.requireNonNull(new File(path).listFiles())).forEach(File::delete);

        System.out.println("======== Peformance Test ========");
        System.out.println("FileCount: 10.000 MP3 (5MB) without Tags");
        System.out.println("SimpleTime: " + simpleResultTime);  //Result: 0,37 Sec.
        System.out.println("ListTime: " + listResultTime);  //Result: 0,237 Sec.
        System.out.printf("Speedup: %.2fx faster%n", (double) simpleResultTime / listResultTime);  //Result: 1,56x faster
    }

}
