# Tagix

Eine umfassende, hochperformante Java-Bibliothek zur Erkennung, Analyse, Verarbeitung und **Verwaltung** von Audio-Tag-Formaten – vollständiges Lesen und Schreiben.

## Voraussetzungen

- **Java 25** (mit Preview-Features: Structured Concurrency / Virtual Threads)
- **Maven** zum Build

## Unterstützte Tag-Formate

| Format | Erkennung | Parsing | Schreiben |
|--------|-----------|---------|-----------|
| ID3v1 / ID3v1.1 | ✓ | ✓ | ✓ |
| ID3v2.2 / ID3v2.3 / ID3v2.4 | ✓ | ✓ | ✓ |
| APEv1 / APEv2 | ✓ | ✓ | ✓ |
| Vorbis Comment (OGG, FLAC) | ✓ | ✓ | ✓ |
| MP4 / iTunes | ✓ | ✓ | ✓ |
| RIFF-INFO | ✓ | ✓ | ✓ |
| BWF (v0, v1, v2) | ✓ | ✓ | ✓ |
| AIFF | ✓ | ✓ | ✓ |
| Lyrics3 (v1, v2) | ✓ | ✓ | ✓ |
| ASF Content/Extended Description | ✓ | ✓ | ✓ |
| DSF Metadata | ✓ | ✓ | ✓ |
| DFF Metadata (DSDIFF) | ✓ | ✓ | ✓ |
| FLAC Application Blocks | ✓ | ✓ | ✓ |
| Matroska Tags | ✓ | ✓ | ✓ |
| WebM Tags | ✓ | ✓ | ✓ |
| WavPack Native | ✓ | ✓ | ✓ |
| TrueAudio | ✓ | — | — |

## Architektur

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

### Schnellstart

#### Tag-Formate erkennen

```java
TagFormatDetector detector = new TagFormatDetector();

// Vollständiger Scan – alle Formate prüfen
List<TagInfo> allTags = detector.fullScan("audio.mp3");

// Komfort-Scan – nur wahrscheinliche Formate für die Dateiendung
List<TagInfo> likelyTags = detector.comfortScan("audio.mp3");

// Benutzerspezifischer Scan – nur bestimmte Formate prüfen
List<TagInfo> specificTags = detector.customScan("audio.mp3",
    TagFormat.ID3V2_3, TagFormat.ID3V2_4, TagFormat.ID3V1);
```

#### Metadaten lesen

```java
MetadataManager manager = new MetadataManager();

// Aus Datei
List<Metadata> metadata = manager.readFromFile("audio.mp3");

// Aus Byte-Array
List<Metadata> metadata = manager.readFromBytes(audioData);

// Aus InputStream (wird automatisch in temporäre Datei gepuffert)
List<Metadata> metadata = manager.readFromInputStream(inputStream, "mp3");

// Aus SeekableDataSource
try (SeekableDataSource source = SeekableDataSources.forPath(Path.of("audio.mp3"))) {
    List<Metadata> tags = manager.readFromSource(source);
}

// Batch-Verarbeitung (parallel mit Virtual Threads)
Map<String, List<Metadata>> results = manager.readFromFiles(
    List.of("file1.mp3", "file2.flac", "file3.wav"));
```

#### Tags schreiben

```java
TagWriter writer = new TagWriter();
Metadata metadata = new GenericMetadata();

// Metadaten befüllen
metadata.setField(MetadataField.TITLE, "Mein Song");
metadata.setField(MetadataField.ARTIST, "Künstler");
metadata.setField(MetadataField.ALBUM, "Mein Album");
metadata.setField(MetadataField.YEAR, "2026");

// Metadaten schreiben (Standard: UPDATE_EXISTING, ID3v2.4)
WriteResult result = writer.writeTags("audio.mp3", metadata);

// Einzelnes Feld aktualisieren
WriteResult result = writer.updateField("audio.mp3", MetadataField.TITLE, "Neuer Titel");

// Tags entfernen
WriteResult result = writer.removeTags("audio.mp3", TagFormat.ID3V2_4);

// Batch-Schreiben (parallel)
Map<String, WriteResult> results = writer.writeTags(
    Map.of("file1.mp3", metadata1, "file2.flac", metadata2));
```

#### Schreib-Konfiguration

```java
// Standard: UPDATE_EXISTING, ID3v2.4, UTF-8
WriteConfiguration config = WriteConfiguration.defaults();

// In-Place-Schreiben
WriteConfiguration config = WriteConfiguration.forInPlace();

// In-Place mit ID3v2.3
WriteConfiguration config = WriteConfiguration.inPlaceId3v23();

// Alle Tags vollständig ersetzen
WriteConfiguration config = WriteConfiguration.replaceAll();

// Nur neue Tags erstellen
WriteConfiguration config = WriteConfiguration.createNew();

// Tags entfernen
WriteConfiguration config = WriteConfiguration.remove();

// Schreibmodi
WriteResult result = writer.writeTags("audio.mp3", metadata, config);
```

### Datenquellen

`SeekableDataSource` abstrahiert den Zugriff auf Dateidaten und unterstützt verschiedene Quellen:

```java
// Datei-Pfad
SeekableDataSource source = SeekableDataSources.forPath(Path.of("audio.mp3"));

// Byte-Array (im Speicher)
SeekableDataSource source = SeekableDataSources.forBytes(data);

// InputStream (wird in temporäre Datei gepuffert, bei close() gelöscht)
SeekableDataSource source = SeekableDataSources.forInputStream(stream, "mp3");

// RandomAccessFile
SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf);
```

Alle Implementierungen sind `AutoCloseable` und sollten mit try-with-resources verwendet werden.

## Batch-Verarbeitung

Die Batch-Methoden (`TagFormatDetector.fullScan(filePaths)`, `MetadataManager.readFromFiles(...)`, `TagWriter.writeTags(...)`) nutzen **Java 25 Structured Concurrency** mit virtuellen Threads:

- Jede Datei wird in einem eigenen Virtual Thread verarbeitet
- Fehler einer Datei beeinflussen nicht die Verarbeitung anderer Dateien
- Einzelne Dateien werden sequentiell verarbeitet (kein Thread-Overhead)
- Ergebnisse bleiben in der ursprünglichen Reihenfolge (`LinkedHashMap`)

## Scan-Modi

| Modus | Beschreibung |
|-------|-------------|
| `FULL_SCAN` | Prüft alle unterstützten Tag-Formate unabhängig von der Dateiendung |
| `COMFORT_SCAN` | Prüft nur wahrscheinliche Formate basierend auf der Dateiendung |
| `CUSTOM_SCAN` | Prüft nur die vom Benutzer angegebenen Formate |

## Schreib-Modi

| Modus | Beschreibung |
|-------|-------------|
| `CREATE_NEW` | Nur neue Tags schreiben, keine bestehenden einbeziehen |
| `UPDATE_EXISTING` | Bestehende Tags aktualisieren oder neue hinzufügen |
| `REPLACE_ALL` | Alle Tags des angegebenen Formats vollständig ersetzen |
| `REMOVE` | Tags des angegebenen Formats entfernen |

## Build

```bash
# Voraussetzung: JDK 25 (z.B. Eclipse Temurin 25)
export JAVA_HOME=/pfad/zu/jdk-25

mvn compile
mvn test
```

## Lizenz

GPL-3.0
