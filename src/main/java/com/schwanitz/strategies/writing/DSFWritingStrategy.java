package com.schwanitz.strategies.writing;

import com.schwanitz.io.*;
import com.schwanitz.interfaces.Metadata;
import com.schwanitz.strategies.parsing.factory.TagParsingStrategyFactory;
import com.schwanitz.strategies.writing.context.TagWritingStrategy;
import com.schwanitz.tagging.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Schreib-Strategie für DSF Metadata (DSD Stream File).
 * <p>
 * DSF-Dateien enthalten eingebettete ID3v2-Tags.
 * Delegiert die eigentliche Tag-Schreibarbeit an {@link ID3WritingStrategy}.
 * </p>
 */
public class DSFWritingStrategy extends AbstractTagWritingStrategy implements TagWritingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(DSFWritingStrategy.class);
    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    private final ID3WritingStrategy id3Writer;

    public DSFWritingStrategy(TagParsingStrategyFactory parsingFactory) {
        super("DSF", parsingFactory);
        this.id3Writer = new ID3WritingStrategy(parsingFactory);
    }

    @Override
    public List<TagFormat> getSupportedWriteFormats() {
        return List.of(TagFormat.DSF_METADATA);
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

        // DSF delegiert an ID3 Writing
        TagFormat id3Format = config.id3Version() == 3 ? TagFormat.ID3V2_3 : TagFormat.ID3V2_4;
        return id3Writer.writeTag(id3Format, metadata, source, existingTag, config);
    }
}
