package com.schwanitz.tagging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WriteConfiguration Tests")
class WriteConfigurationTest {

    @Test
    @DisplayName("defaults() erstellt korrekte Standardwerte")
    void defaults() {
        WriteConfiguration config = WriteConfiguration.defaults();
        assertEquals(WriteMode.UPDATE_EXISTING, config.mode());
        assertFalse(config.inPlace());
        assertEquals(4, config.id3Version());
        assertEquals("UTF-8", config.encoding());
        assertTrue(config.preserveExistingTags());
    }

    @Test
    @DisplayName("inPlace() erstellt In-Place-Konfiguration")
    void inPlace() {
        WriteConfiguration config = WriteConfiguration.forInPlace();
        assertEquals(WriteMode.UPDATE_EXISTING, config.mode());
        assertTrue(config.inPlace());
        assertEquals(4, config.id3Version());
    }

    @Test
    @DisplayName("inPlaceId3v23() erstellt ID3v2.3 In-Place-Konfiguration")
    void inPlaceId3v23() {
        WriteConfiguration config = WriteConfiguration.inPlaceId3v23();
        assertTrue(config.inPlace());
        assertEquals(3, config.id3Version());
        assertTrue(config.preserveExistingTags());
    }

    @Test
    @DisplayName("inPlaceId3v24() erstellt ID3v2.4 In-Place-Konfiguration")
    void inPlaceId3v24() {
        WriteConfiguration config = WriteConfiguration.inPlaceId3v24();
        assertTrue(config.inPlace());
        assertEquals(4, config.id3Version());
    }

    @Test
    @DisplayName("replaceAll() erstellt REPLACE_ALL-Konfiguration")
    void replaceAll() {
        WriteConfiguration config = WriteConfiguration.replaceAll();
        assertEquals(WriteMode.REPLACE_ALL, config.mode());
        assertFalse(config.inPlace());
        assertFalse(config.preserveExistingTags());
    }

    @Test
    @DisplayName("remove() erstellt REMOVE-Konfiguration")
    void remove() {
        WriteConfiguration config = WriteConfiguration.remove();
        assertEquals(WriteMode.REMOVE, config.mode());
        assertFalse(config.preserveExistingTags());
    }

    @Test
    @DisplayName("createNew() erstellt CREATE_NEW-Konfiguration")
    void createNew() {
        WriteConfiguration config = WriteConfiguration.createNew();
        assertEquals(WriteMode.CREATE_NEW, config.mode());
        assertFalse(config.preserveExistingTags());
    }

    @Test
    @DisplayName("Record-Instanziierung mit allen Parametern")
    void recordInstantiation() {
        WriteConfiguration config = new WriteConfiguration(
                WriteMode.REPLACE_ALL, true, 3, "ISO-8859-1", false);
        assertEquals(WriteMode.REPLACE_ALL, config.mode());
        assertTrue(config.inPlace());
        assertEquals(3, config.id3Version());
        assertEquals("ISO-8859-1", config.encoding());
        assertFalse(config.preserveExistingTags());
    }

    @Test
    @DisplayName("equals und hashCode")
    void equalsAndHashCode() {
        WriteConfiguration a = WriteConfiguration.defaults();
        WriteConfiguration b = WriteConfiguration.defaults();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
