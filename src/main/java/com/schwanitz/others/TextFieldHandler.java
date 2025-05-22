package com.schwanitz.others;

import com.schwanitz.interfaces.FieldHandler;

import java.nio.charset.StandardCharsets;

public class TextFieldHandler implements FieldHandler<String> {
    private final String key;

    public TextFieldHandler(String key) {
        this.key = key;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public String readData(byte[] data) {
        return new String(data, StandardCharsets.UTF_8);
    }

    @Override
    public byte[] writeData(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}