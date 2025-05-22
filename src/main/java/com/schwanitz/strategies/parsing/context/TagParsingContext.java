package com.schwanitz.strategies.parsing.context;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.strategies.parsing.*;
import com.schwanitz.tagging.TagFormat;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

public class TagParsingContext {
    private final List<TagParsingStrategy> strategies = new ArrayList<>();

    public TagParsingContext() {
        // Alle verfügbaren Strategien registrieren
        strategies.add(new ID3ParsingStrategy());           // ID3v1/v1.1/v2.2/v2.3/v2.4
        strategies.add(new VorbisParsingStrategy());        // Vorbis Comments (OGG/FLAC)
        strategies.add(new Lyrics3ParsingStrategy());       // Lyrics3v1/v2
        strategies.add(new APEParsingStrategy());           // APEv1/v2
        strategies.add(new MP4ParsingStrategy());           // MP4/iTunes Tags
        strategies.add(new RIFFInfoParsingStrategy());      // RIFF INFO Chunks (WAV)
        strategies.add(new BWFParsingStrategy());           // Broadcast Wave Format
        strategies.add(new AIFFMetadataParsingStrategy());  // AIFF Metadata Chunks
    }

    public void addStrategy(TagParsingStrategy strategy) {
        strategies.add(strategy);
    }

    public Metadata parseTag(TagFormat format, RandomAccessFile file, long offset, long size) throws IOException {
        for (TagParsingStrategy strategy : strategies) {
            if (strategy.canHandle(format)) {
                return strategy.parseTag(format, file, offset, size);
            }
        }
        throw new UnsupportedOperationException("Keine Strategie für Format gefunden: " + format);
    }
}