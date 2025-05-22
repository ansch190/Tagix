package com.schwanitz.interfaces;

public interface FieldHandler<T> {
    String getKey();
    T readData(byte[] data);
    byte[] writeData(T value);
}