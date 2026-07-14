package com.schwanitz.tagging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WriteResult Tests")
class WriteResultTest {

    @Test
    @DisplayName("success() erstellt Erfolgsergebnis")
    void success() {
        WriteResult result = WriteResult.success(TagFormat.ID3V2_4, 100, 200);
        assertTrue(result.success());
        assertEquals(TagFormat.ID3V2_4, result.format());
        assertEquals(100, result.oldTagSize());
        assertEquals(200, result.newTagSize());
        assertNotNull(result.message());
    }

    @Test
    @DisplayName("failure() erstellt Fehlerergebnis")
    void failure() {
        WriteResult result = WriteResult.failure(TagFormat.MP4, "Fehler");
        assertFalse(result.success());
        assertEquals(TagFormat.MP4, result.format());
        assertEquals(0, result.oldTagSize());
        assertEquals(0, result.newTagSize());
        assertEquals("Fehler", result.message());
    }

    @Test
    @DisplayName("success() mit benutzerdefinierter Meldung")
    void successWithMessage() {
        WriteResult result = WriteResult.success(TagFormat.APEV2, 50, 100, "OK");
        assertTrue(result.success());
        assertEquals(50, result.oldTagSize());
        assertEquals(100, result.newTagSize());
        assertEquals("OK", result.message());
    }

    @Test
    @DisplayName("Record-Instanziierung")
    void recordInstantiation() {
        WriteResult result = new WriteResult(true, TagFormat.VORBIS_COMMENT, 0, 0, null);
        assertTrue(result.success());
        assertEquals(TagFormat.VORBIS_COMMENT, result.format());
        assertNull(result.message());
    }
}
