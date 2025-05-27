package com.schwanitz.testutils;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

/**
 * Einfacher Generator für valide MP3-Dateien ohne Tags mit konfigurierbarer Dateigröße
 */
public class Mp3FileGenerator {

    // Standard MP3 Frame Header für:
    // - MPEG-1 Audio Layer III
    // - 128 kbps Bitrate
    // - 44.1 kHz Sample Rate
    // - Stereo
    // - No Protection, No Padding, No Copyright, Original
    private static final byte[] MP3_FRAME_HEADER = {
            (byte)0xFF, (byte)0xFB, (byte)0x90, (byte)0x00
    };

    // Frame Größe für 128 kbps bei 44.1 kHz = 417 bytes
    private static final int FRAME_SIZE = 417;
    private static final int FRAME_DATA_SIZE = FRAME_SIZE - 4; // -4 für Header

    private static final Random random = new Random();

    /**
     * Generiert eine MP3-Datei mit der angegebenen Größe in MB
     *
     * @param outputPath Pfad zur Ausgabedatei
     * @param sizeInMB Gewünschte Dateigröße in Megabytes
     * @throws IOException bei Schreibfehlern
     */
    public static void getFileBySize(Path outputPath, double sizeInMB) throws IOException {
        long targetSizeBytes = (long)(sizeInMB * 1024 * 1024);
        int frameCount = (int)(targetSizeBytes / FRAME_SIZE);

        // Mindestens ein Frame
        if (frameCount < 1) {
            frameCount = 1;
        }

        try (RandomAccessFile raf = new RandomAccessFile(outputPath.toFile(), "rw")) {
            writeRandomFrames(raf, frameCount);
        }

        //System.out.printf("MP3 created: %.2f MB (%d Frames, ~%.1f Seconds)%n",
        //        targetSizeBytes / (1024.0 * 1024.0),
        //        frameCount,
        //        frameCount * 0.026);
    }

    /**
     * Generiert eine MP3-Datei mit der gewünschten Dauer in Sekunden
     *
     * @param outputPath Pfad zur Ausgabedatei
     * @param durationSeconds Gewünschte Dauer in Sekunden
     * @throws IOException bei Schreibfehlern
     */
    public static void getFileByDuration(Path outputPath, double durationSeconds) throws IOException {
        // Bei 44.1 kHz und Layer III: 1152 Samples pro Frame = ~0.026 Sekunden pro Frame
        int frameCount = (int) Math.ceil(durationSeconds / 0.026);

        try (RandomAccessFile raf = new RandomAccessFile(outputPath.toFile(), "rw")) {
            writeRandomFrames(raf, frameCount);
        }

        double actualSize = frameCount * FRAME_SIZE / (1024.0 * 1024.0);
        //System.out.printf("MP3 created: %.2f MB (%d Frames, %.1f Seconds)%n",
        //        actualSize, frameCount, durationSeconds);
    }

    /**
     * Schreibt MP3 Frames mit zufälligen Audiodaten
     */
    private static void writeRandomFrames(RandomAccessFile raf, int frameCount) throws IOException {
        //System.out.printf("Generiere %d MP3-Frames mit zufälligen Audiodaten...%n", frameCount);

        // Progress-Anzeige für große Dateien
        int progressStep = Math.max(1, frameCount / 10);

        for (int i = 0; i < frameCount; i++) {
            // Progress-Update
            //if (i % progressStep == 0) {
            //    int progress = (int)((double)i / frameCount * 100);
            //    System.out.printf("Progress: %d%% (%d/%d Frames)%n", progress, i, frameCount);
            //}

            // Frame Header schreiben
            raf.write(MP3_FRAME_HEADER);

            // Zufällige Frame Data schreiben
            byte[] randomFrameData = createRandomFrameData();
            raf.write(randomFrameData);
        }

        //System.out.println("100% - MP3 created!");
    }

    /**
     * Erstellt zufällige aber valide MP3 Frame-Daten
     */
    private static byte[] createRandomFrameData() {
        byte[] data = new byte[FRAME_DATA_SIZE];

        // Fülle mit zufälligen Bytes
        random.nextBytes(data);

        // Vermeiden von 0xFF am Anfang (könnte als falscher Sync interpretiert werden)
        // und anderen problematischen Sync-Patterns
        for (int i = 0; i < data.length - 1; i++) {
            // Verhindere 0xFF 0xFx Patterns (außer am echten Frame-Anfang)
            if ((data[i] & 0xFF) == 0xFF && (data[i + 1] & 0xF0) == 0xF0) {
                data[i] = (byte)(0xFE); // Ändere zu 0xFE
            }
        }

        return data;
    }

    /**
     * Beispiel-Verwendung und Demo
     */
    public static void main(String[] args) {
        try {
            generate1000TestFiles();
            //getFileByDuration(Path.of("src/test/resources/testfiles/mp3/test_0.mp3"), 200);
            //getFileBySize(Path.of("src/test/resources/testfiles/mp3/test_1.mp3"), 5);

        } catch (IOException e) {
            System.err.println("Fehler beim Generieren der MP3-Dateien: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void generate1000TestFiles() throws IOException {
        Path baseDir = Paths.get("src/test/resources/testfiles/mp3");

        //System.out.println("Generiere 1000 MP3-Dateien (5MB each) in: " + baseDir);
        //System.out.println("Geschätzte Gesamtgröße: ~5GB");

        for (int i = 0; i < 1000; i++) {
            String filename = generateRandomFilename() + ".mp3";
            Path filePath = baseDir.resolve(filename);

            getFileBySize(filePath, 5.0);

            // Progress alle 50 Dateien
            //if ((i + 1) % 50 == 0) {
                //System.out.printf("Progress: %d/1000 Dateien erstellt (%.1f%%)%n",
                //        i + 1, (i + 1) / 10.0);
            //}
        }

        System.out.println("✅ Alle 1000 MP3-Dateien erfolgreich erstellt!");
    }

    private static String generateRandomFilename() {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.#+&";
        StringBuilder filename = new StringBuilder(30);

        for (int i = 0; i < 30; i++) {
            filename.append(chars.charAt(random.nextInt(chars.length())));
        }

        return filename.toString();
    }
    
}