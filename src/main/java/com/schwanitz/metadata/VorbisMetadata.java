package com.schwanitz.metadata;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.others.MetadataField;
import com.schwanitz.tagging.TagFormat;

import java.util.ArrayList;
import java.util.List;

public class VorbisMetadata implements Metadata {
    private final List<MetadataField<?>> fields = new ArrayList<>();

    @Override
    public String getTagFormat() {
        return TagFormat.VORBIS_COMMENT.getFormatName();
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