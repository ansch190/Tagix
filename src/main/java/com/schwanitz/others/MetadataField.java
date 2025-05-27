package com.schwanitz.others;

import com.schwanitz.interfaces.FieldHandler;

public class MetadataField<T> {

    private final String key;
    private T value;
    private final FieldHandler<T> handler;

    public MetadataField(String key, T value, FieldHandler<T> handler) {
        this.key = key;
        this.value = value;
        this.handler = handler;
    }

    public String getKey() {
        return key;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public FieldHandler<T> getHandler() {
        return handler;
    }
}