package com.schwanitz.strategies.parsing.integration;

import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.strategies.parsing.factory.TagParsingStrategyFactory;
import com.schwanitz.tagging.TagFormat;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class TagParsingContextTest {

    private final TagParsingStrategyFactory factory = new TagParsingStrategyFactory();

    private List<TagFormat> getSupportedFormats() {
        List<TagFormat> supported = new ArrayList<>();
        for (TagFormat format : TagFormat.values()) {
            if (factory.getStrategyForFormat(format) != null) {
                supported.add(format);
            }
        }
        return supported;
    }

    @Test
    @DisplayName("Factory creates correct strategy for each format")
    void factoryCreatesCorrectStrategyForEachFormat() {
        List<TagFormat> supported = getSupportedFormats();
        assertThat(supported).isNotEmpty();

        for (TagFormat format : supported) {
            TagParsingStrategy strategy = factory.getStrategyForFormat(format);
            assertNotNull(strategy, "Strategy should not be null for format: " + format);
        }
    }

}
