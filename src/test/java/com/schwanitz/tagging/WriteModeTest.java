package com.schwanitz.tagging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WriteMode Tests")
class WriteModeTest {

    @Test
    @DisplayName("Alle WriteMode-Werte vorhanden")
    void allValues() {
        WriteMode[] modes = WriteMode.values();
        assertEquals(4, modes.length);
    }

    @Test
    @DisplayName("Werte haben korrekte Namen")
    void valueNames() {
        assertNotNull(WriteMode.CREATE_NEW);
        assertNotNull(WriteMode.UPDATE_EXISTING);
        assertNotNull(WriteMode.REPLACE_ALL);
        assertNotNull(WriteMode.REMOVE);
    }

    @Test
    @DisplayName("valueOf funktioniert")
    void valueOf() {
        assertEquals(WriteMode.CREATE_NEW, WriteMode.valueOf("CREATE_NEW"));
        assertEquals(WriteMode.UPDATE_EXISTING, WriteMode.valueOf("UPDATE_EXISTING"));
        assertEquals(WriteMode.REPLACE_ALL, WriteMode.valueOf("REPLACE_ALL"));
        assertEquals(WriteMode.REMOVE, WriteMode.valueOf("REMOVE"));
    }
}
