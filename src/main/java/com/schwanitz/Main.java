package com.schwanitz;

import com.schwanitz.tagging.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Demonstrationseinstiegspunkt für die Tag-Erkennung in Audiodateien.
 * <p>
 * Durchsucht ein Verzeichnis nach Audiodateien und gibt die erkannten
 * Tag-Formate für jede Datei aus. Dient als Beispiel für die Verwendung
 * des {@link TagFormatDetector} im Vollscan-Modus.
 */
public class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    /**
     * Hauptmethode, die ein Verzeichnis nach Audiodateien durchsucht und erkannte Tags ausgibt.
     *
     * @param args Kommandozeilenargumente (wird nicht verwendet)
     * @throws IOException wenn das Verzeichnis nicht gelesen werden kann
     */
    public static void main(String[] args) throws IOException {
        //String path = "src/main/resources/musicFiles";
        //String path = "/home/andreas/Musik/tagging/";
        String path = "/home/andreas/pCloudDrive/Audio/Alben";
        File f = new File(path);
        List<String> files = Files.walk(f.toPath())
                .filter(Files::isRegularFile)
                .map(Path::toString)
                .collect(Collectors.toList());

        LOG.info("Files: {}", files.size());

        TagFormatDetector detector = new TagFormatDetector();
        Map<String,List<TagInfo>> results;
        results = detector.fullScan(files);
        //results = detector.comfortScan(files);
        //results = detector.customScan(files, TagFormat.ID3V2_3, TagFormat.ID3V1_1, TagFormat.ID3V1, TagFormat.ID3V2_4, TagFormat.ID3V2_2);

        for (String s : results.keySet()){
          for (TagInfo t: results.get(s)){
              LOG.info("File: {}, Tags: {}",s , t.getFormat());
          }
        }
    }

}