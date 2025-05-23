package com.schwanitz.strategies.parsing;

import org.junit.platform.suite.api.*;

/**
 * Test-Suite für VorbisParsingStrategy
 *
 * Diese Test-Suite deckt folgende Bereiche ab:
 *
 * 1. Standard-Vorbis-Comment-Tests:
 *    - Parsing von Standardfeldern (TITLE, ARTIST, ALBUM, etc.) in Ogg und FLAC
 *    - Mehrere Werte für denselben Schlüssel (z. B. mehrere ARTIST-Felder)
 *    - Sonderzeichen und UTF-8-Encoding
 *    - Unbekannte oder benutzerdefinierte Felder
 *
 * 2. Sonderfälle:
 *    - Leerer Vendor-String
 *    - Leere Vorbis-Comments (nur Vendor-String)
 *    - Große Kommentarfelder
 *    - Ungültige Feldnamen
 *
 * 3. Fehlerbehandlung:
 *    - Korrupte Tags (ungültige Vendor-Länge, falscher Header)
 *    - Ungültige oder fehlende Feldlängen
 *
 * 4. Format-Unterstützung:
 *    - Prüfung der canHandle-Methode für VORBIS_COMMENT und andere Formate
 */
@Suite
@SelectClasses(VorbisParsingStrategyTest.class)
@IncludeTags({"unit", "vorbis", "parsing"})
@ExcludeTags({"integration"})
public class VorbisParsingTestSuite {
}