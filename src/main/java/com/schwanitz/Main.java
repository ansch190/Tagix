package com.schwanitz;

import com.schwanitz.tagging.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Main {

    private static final Logger Log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws IOException {
        String path = "src/test/resources/testfiles/mp3";
        File f = new File(path);
        List<File> files = List.of(Objects.requireNonNull(f.listFiles()));

        Log.info("Files: {}", files.size());

        Map<String,List<TagInfo>> results = new HashMap<>();
        for (File file : files) {
          results.put(file.getAbsolutePath(), TagFormatDetector.fullScan(file.getAbsolutePath()));
        }

        for (String s : results.keySet()){
          for (TagInfo t: results.get(s)){
              Log.info("File: {}, Tags: {}",s , t.toString());
          }
        }
    }

}