package com.schwanitz.metadata;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.others.MetadataField;
import com.schwanitz.tagging.TagFormat;

import java.util.ArrayList;
import java.util.List;

public class Lyrics3Metadata implements Metadata {

    private final List<MetadataField<?>> fields = new ArrayList<>();

    private final TagFormat format;

    public Lyrics3Metadata(TagFormat format) {
        this.format = format;
    }

    @Override
    public String getTagFormat() {
        return format.getFormatName();
    }

    @Override
    public List<MetadataField<?>> getFields() {
        return fields;
    }

    @Override
    public void addField(MetadataField<?> field) {
        fields.add(field);
    }
}