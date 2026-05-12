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
        strategies.add(new ID3ParsingStrategy());
        strategies.add(new VorbisParsingStrategy());
        strategies.add(new Lyrics3ParsingStrategy());
        strategies.add(new APEParsingStrategy());
        strategies.add(new MP4ParsingStrategy());
        strategies.add(new RIFFInfoParsingStrategy());
        strategies.add(new BWFParsingStrategy());
        strategies.add(new AIFFMetadataParsingStrategy());
        strategies.add(new ASFParsingStrategy());
        strategies.add(new DSFParsingStrategy());
        strategies.add(new DFFParsingStrategy());
        strategies.add(new MatroskaParsingStrategy());
        strategies.add(new WavPackParsingStrategy());
        strategies.add(new FLACApplicationParsingStrategy());
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