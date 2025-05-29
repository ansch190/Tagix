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

public class Main {

    private static final Logger Log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws IOException {
        //String path = "src/main/resources/musicFiles";
        //String path = "/home/andreas/Musik/tagging/";
        String path = "/home/andreas/pCloudDrive/Audio/Alben";
        File f = new File(path);
        List<String> files = Files.walk(f.toPath())
                .filter(Files::isRegularFile)
                .map(Path::toString)
                .collect(Collectors.toList());

        Log.info("Files: {}", files.size());

        Map<String,List<TagInfo>> results;
        results = TagFormatDetector.fullScan(files);
        //results = TagFormatDetector.comfortScan(files);
        //results = TagFormatDetector.customScan(files, TagFormat.ID3V2_3, TagFormat.ID3V1_1, TagFormat.ID3V1, TagFormat.ID3V2_4, TagFormat.ID3V2_2);

        for (String s : results.keySet()){
          for (TagInfo t: results.get(s)){
              Log.info("File: {}, Tags: {}",s , t.getFormat());
          }
        }
    }

}