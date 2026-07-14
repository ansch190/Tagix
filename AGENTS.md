# Agents.md

## Build

```bash
"C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd" compile
"C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd" test
```

JAVA_HOME: `C:\Users\andre\.jdks\temurin-25.0.3`

## Test-Status

540 Tests, 0 Fehler. **Vor jedem Commit `mvn test` ausführen.**

## Architektur

Strategy Pattern mit Pipeline: Detection → Parsing → Metadata.
TagWriter-Orchestrator für Schreibvorgänge mit parallelisierter Batch-Verarbeitung.

```
com.schwanitz
├── api                     ─ Zentrale API (MetadataManager, TagWriter)
├── io                      ─ Abstraktion für Datenquellen/Senken
├── interfaces              ─ Gemeinsame Schnittstellen (Metadata, FieldHandler)
├── metadata                ─ Metadaten-Modelle und Field-Handler
├── tagging                 ─ Tag-Formate, Erkennung, Scan-Konfiguration, Schreib-Konfiguration
└── strategies
    ├── detection           ─ Erkennungsstrategien (je Format)
    ├── parsing             ─ Parsing-Strategien (je Format)
    └── writing             ─ Schreib-Strategien (je Format)
```

## Schicht-Aufteilung

### IO-Schicht (Lesen)
- `SeekableDataSource` – abstrakte Datenquelle (Datei, Bytes, InputStream)
- `SourceReader` – sequenzieller Leseadapter
- `BinaryDataReader` – Low-Level-Lese-Utilities (LE/BE/Synchsafe)

### IO-Schicht (Schreiben)
- `SeekableDataSink` – Schreib-Schnittstelle
- `SeekableDataSinks` – Factory (FileChannelSink, TempFileSink)
- `ByteArraySink` – In-Memory-Sink (Standard für Schreibstrategien)
- `SourceWriter` – sequenzieller Schreibeadapter (AutoCloseable)
- `BinaryDataWriter` – Low-Level-Schreib-Utilities

### Writing-Strategien
15 Strategien – eine pro Format:

| Klasse | Formate |
|--------|---------|
| `ID3WritingStrategy` | ID3v1, ID3v1.1, ID3v2.2, ID3v2.3, ID3v2.4 |
| `VorbisWritingStrategy` | OGG Vorbis, FLAC |
| `APEWritingStrategy` | APEv1, APEv2 |
| `MP4WritingStrategy` | MP4/iTunes ilst |
| `RIFFWritingStrategy` | RIFF-INFO, BWF |
| `ASFWritingStrategy` | ASF Content/Extended Description |
| `AIFFWritingStrategy` | AIFF IFF-Chunks |
| `MatroskaWritingStrategy` | Matroska, WebM |
| `DSFWritingStrategy` | Delegiert an ID3 |
| `DFFWritingStrategy` | Delegiert an ID3 |
| `WavPackWritingStrategy` | WavPack Native |
| `Lyrics3WritingStrategy` | Lyrics3 v1, v2 |

### TagWriter-Orchestrator
- `TagWriter.writeTags()` – Volles Metadata-Objekt schreiben
- `TagWriter.updateField()` – Einzelnes Feld aktualisieren
- `TagWriter.removeTags()` – Tags entfernen
- Batch-Methoden mit `StructuredTaskScope` für parallele Verarbeitung

### Schreib-Konfiguration
- `WriteMode`: `CREATE_NEW`, `UPDATE_EXISTING`, `REPLACE_ALL`, `REMOVE`
- `WriteConfiguration` – Record mit Mode, In-Place, ID3-Version, Encoding
- `WriteResult` – Ergebnis-Record (success, format, oldSize, newSize, message)
- `WriteConfiguration.defaults()` – UPDATE_EXISTING, ID3v2.4, UTF-8

## Konventionen

### Sprache
- **Deutsch** für Javadoc, Code-Kommentare, Variablennamen wo sinnvoll
- Klassen und Methoden auf Englisch (Java-Standard)

### Code-Stil
- Keine `.bak`-Dateien – Temp-File + atomares Ersetzen oder In-Memory (ByteArraySink)
- **Windows-Kompatibilität**: Schreibstrategien verwenden `ByteArraySink` (In-Memory) statt `TempFileSink`
- `WriteConfiguration.inPlace()` wurde zu `WriteConfiguration.forInPlace()` umbenannt (Konflikt mit Record-Accessor)
- TagFormat-lookup über Enum-Name-Loop, nicht `TagFormat.valueOf()` (Display-Name ≠ Enum-Constant)
- Jede Schreibstrategie erbt von `AbstractTagWritingStrategy`
- ID3-Standard: **v2.4** (nicht v2.3)

### Architektur-Entscheidungen
- DSF/DFF delegieren Schreibvorgänge an `ID3WritingStrategy`
- `TagWritingStrategyFactory` mapped Format → Strategie
- `AbstractTagWritingStrategy.mergeMetadata()` führt Parsing + Merging durch
- Schreibstrategien lesen den bestehenden Tag, mergen mit neuen Daten, schreiben komplett neu
- Keine `.bak`-Dateien nötig – In-Memory-Build vermeidet Datei-Konflikte

## Bekannte Fallstricke

1. **Windows `AccessDeniedException`**: Temp-File + `Files.move()` funktioniert nicht, wenn Source-File noch offen ist. Lösung: `ByteArraySink` (In-Memory)
2. **`TagFormat.valueOf()`**: Display-Name `"ID3v2.4"` ≠ Enum-Constant `ID3V2_4`. Immer über Namensschleife suchen
3. **`SourceWriter.close()`**: Muss implementiert sein – ohne Close bleibt Buffer unter Umständen nicht geschrieben
4. **Synchsafe-Integers**: Für ID3v2.4 verwendet. Testwerte klein halten (z.B. 15, 127, 15 statt große Werte)
