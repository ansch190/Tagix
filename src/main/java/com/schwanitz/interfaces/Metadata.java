package com.schwanitz.interfaces;

import com.schwanitz.others.MetadataField;
import java.util.List;

public interface Metadata {

    String getTagFormat();
    List<MetadataField<?>> getFields();
    void addField(MetadataField<?> field);
}