package com.schwanitz.strategies.writing;

import com.schwanitz.io.*;
import com.schwanitz.interfaces.Metadata;
import com.schwanitz.strategies.parsing.factory.TagParsingStrategyFactory;
import com.schwanitz.strategies.writing.context.TagWritingStrategy;
import com.schwanitz.tagging.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Schreib-Strategie für DFF Metadata (DSD Interchange File Format).
 * <p>
 * DFF-Dateien enthalten eingebettete ID3v2-Tags in DIIN/DITI-Chunks.
 * Delegiert die eigentliche Tag-Schreibarbeit an {@link ID3WritingStrategy}.
 * </p>
 */
public class DFFWritingStrategy extends AbstractTagWritingStrategy implements TagWritingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(DFFWritingStrategy.class);

    private final ID3WritingStrategy id3Writer;

    public DFFWritingStrategy(TagParsingStrategyFactory parsingFactory) {
        super("DFF", parsingFactory);
        this.id3Writer = new ID3WritingStrategy(parsingFactory);
    }

    @Override
    public List<TagFormat> getSupportedWriteFormats() {
        return List.of(TagFormat.DFF_METADATA);
    }

    @Override
    public boolean supportsInPlaceWrite(TagFormat format) {
        return false;
    }

    @Override
    public WriteResult writeTag(TagFormat format, Metadata metadata,
                                 SeekableDataSource source, TagInfo existingTag,
                                 WriteConfiguration config) throws IOException {
        validateInput(metadata, source, format);
        Objects.requireNonNull(config, "config must not be null");

        TagFormat id3Format = config.id3Version() == 3 ? TagFormat.ID3V2_3 : TagFormat.ID3V2_4;
        return id3Writer.writeTag(id3Format, metadata, source, existingTag, config);
    }
}
