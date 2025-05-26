# Tag-Format-Erkennung: Scan-Modi Dokumentation

## Übersicht

Die Tagix-Bibliothek bietet drei verschiedene Scan-Modi für die Tag-Format-Erkennung, die je nach Anwendungsfall optimale Performance und Vollständigkeit bieten:

1. **Comfort Scan** - Standard-Modus für normale Anwendungen
2. **Full Scan** - vollständige Analyse aller möglichen Tag-Formate
3. **Custom Scan** - Benutzerdefinierte Auswahl spezifischer Tag-Formate

## Scan-Modi im Detail

### 1. Comfort Scan (Empfohlen)

**Beschreibung:** Prüft nur die wahrscheinlichsten Tag-Formate für die jeweilige Dateiendung.

**Vorteile:**
- Schnellste Performance
- Geringster Ressourcenverbrauch
- Findet in 95% der Fälle alle relevanten Tags
- Optimiert für bekannte Dateiformate

**Verwendung:**
```java
// Direkte Konfiguration
ScanConfiguration config = ScanConfiguration.comfortScan();
List<TagInfo> tags = TagFormatDetector.detectTagFormats("song.mp3", config);

// Convenience-Methode
List<TagInfo> tags = TagFormatDetector.detectTagFormatsComfortScan("song.mp3");

// MetadataManager
MetadataManager manager = new MetadataManager();
manager.readFromFile("song.mp3"); // Standard = Comfort Scan
```

**Prioritäten nach Dateiendung:**

| Dateiendung | Prioritätsreihenfolge |
|-------------|----------------------|
| `.mp3` | ID3v2.3 → ID3v2.4 → ID3v1 → ID3v1.1 → APEv2 → Lyrics3v2 |
| `.wav` | RIFF INFO → BWF v2 → BWF v1 → BWF v0 → ID3v2.3 |
| `.ogg/.opus/.spx` | Vorbis Comment (einziger Standard) |
| `.flac` | Vorbis Comment → ID3v2.3 → ID3v2.4 → APEv2 |
| `.m4a/.mp4` | MP4 iTunes Tags (einziger Standard) |
| `.aiff/.aif` | AIFF Metadata → ID3v2.3 → ID3v2.4 |

### 2. Full Scan

**Beschreibung:** Prüft alle verfügbaren Tag-Formate in globaler Prioritätsreihenfolge, unabhängig von der Dateiendung.

**Vorteile:**
- Findet garantiert alle vorhandenen Tags
- Erkennt auch ungewöhnliche Tag-Kombinationen
- Ideal für Analyse-Tools und Debugging
- Unterstützt experimentelle/seltene Formate

**Nachteile:**
- Langsamer als Comfort Scan
- Höherer Ressourcenverbrauch

**Verwendung:**
```java
// Direkte Konfiguration
ScanConfiguration config = ScanConfiguration.fullScan();
List<TagInfo> tags = TagFormatDetector.detectTagFormats("song.mp3", config);

// Convenience-Methode  
List<TagInfo> tags = TagFormatDetector.detectTagFormatsFullScan("song.mp3");

// MetadataManager
MetadataManager manager = new MetadataManager();
manager.readFromFileFullScan("song.mp3");
```

**Globale Prioritätsreihenfolge:**
1. ID3v2.3 (häufigstes Format)
2. ID3v2.4 (moderner Standard)
3. ID3v1 (Legacy-Unterstützung)
4. ID3v1.1 (mit Track-Nummer)
5. ID3v2.2 (ältere Version)
6. Vorbis Comment (OGG/FLAC)
7. MP4 iTunes Tags
8. APEv2/APEv1
9. RIFF INFO, BWF
10. Seltene Formate (AIFF, Lyrics3)

### 3. Custom Scan

**Beschreibung:** Prüft nur die vom Benutzer explizit angegebenen Tag-Formate.

**Vorteile:**
- Maximale Kontrolle über den Scan-Prozess
- Optimale Performance für spezifische Anwendungsfälle
- Reduziert false positives
- Ideal für spezialisierte Anwendungen

**Verwendung:**
```java
// Array-Syntax
ScanConfiguration config = ScanConfiguration.customScan(
    TagFormat.ID3V2_3, TagFormat.ID3V2_4, TagFormat.VORBIS_COMMENT
);

// Listen-Syntax
List<TagFormat> formats = Arrays.asList(
    TagFormat.ID3V2_4, TagFormat.MP4, TagFormat.APEV2
);
ScanConfiguration config = ScanConfiguration.customScan(formats);

// Convenience-Methoden
List<TagInfo> tags = TagFormatDetector.detectTagFormatsCustomScan(
    "song.mp3", TagFormat.ID3V2_3, TagFormat.ID3V1
);

// MetadataManager
manager.readFromFileCustomScan("song.mp3", TagFormat.ID3V2_4, TagFormat.APEV2);
```

**Anwendungsbeispiele:**
```java
// Nur moderne Formate
ScanConfiguration modern = ScanConfiguration.customScan(
    TagFormat.ID3V2_4, TagFormat.VORBIS_COMMENT, TagFormat.MP4
);

// Nur ID3-Familie
ScanConfiguration id3Only = ScanConfiguration.customScan(
    TagFormat.ID3V2_3, TagFormat.ID3V2_4, TagFormat.ID3V1, TagFormat.ID3V1_1
);

// Verlustfreie Audio-Formate
ScanConfiguration lossless = ScanConfiguration.customScan(
    TagFormat.VORBIS_COMMENT, TagFormat.APEV2, TagFormat.BWF_V2
);
```

## Erweiterte Features

### Batch-Verarbeitung

Alle Scan-Modi unterstützen die Verarbeitung mehrerer Dateien:

```java
List<String> filePaths = Arrays.asList("song1.mp3", "song2.flac", "song3.wav");
ScanConfiguration config = ScanConfiguration.comfortScan();

// TagFormatDetector
Map<String, List<TagInfo>> results = TagFormatDetector.detectTagFormats(filePaths, config);

// MetadataManager  
MetadataManager manager = new MetadataManager();
Map<String, Integer> metadataCount = manager.readFromFiles(filePaths, config);
```

### Caching

Das Caching-System optimiert wiederholte Zugriffe:

```java
// Cache-Statistiken
int cacheSize = TagFormatDetector.getCacheSize();

// Cache-Management
TagFormatDetector.clearCache();
TagFormatDetector.removeCacheEntry("specific-file.mp3");
```

### Prioritäts-Management

Anpassung der Scan-Prioritäten für spezielle Anwendungsfälle:

```java
// Neue Dateiendung hinzufügen
List<TagFormat> customPriority = Arrays.asList(
    TagFormat.ID3V2_4, TagFormat.VORBIS_COMMENT
);
FormatPriorityManager.addExtensionPriority("newformat", customPriority);

// Unterstützte Endungen prüfen
boolean supported = FormatPriorityManager.isExtensionSupported("mp3");
List<String> allSupported = FormatPriorityManager.getSupportedExtensions();
```

## Performance-Vergleich

| Scan-Modus | Relative Geschwindigkeit | Vollständigkeit | Ressourcenverbrauch |
|------------|-------------------------|-----------------|-------------------|
| Comfort | 100% (Baseline) | 95% | Niedrig |
| Full | 60-80% | 100% | Hoch |
| Custom | 120-200%* | Variable | Sehr niedrig |

*Abhängig von der Anzahl der gewählten Formate

## Empfehlungen

### Normale Medienplayer/Bibliotheken
```java
// Standard-Verwendung
manager.readFromFile(filePath); // = Comfort Scan
```

### Analyse-Tools/Debugging
```java
// Vollständige Analyse
manager.readFromFileFullScan(filePath);
```

### Spezielle Anwendungen
```java
// Podcast-Player (nur moderne Formate)
manager.readFromFileCustomScan(filePath, 
    TagFormat.ID3V2_4, TagFormat.MP4, TagFormat.VORBIS_COMMENT);

// Audioproduktion (professionelle Formate)
manager.readFromFileCustomScan(filePath,
    TagFormat.BWF_V2, TagFormat.BWF_V1, TagFormat.RIFF_INFO, TagFormat.AIFF_METADATA);

// Legacy-System-Unterstützung
manager.readFromFileCustomScan(filePath,
    TagFormat.ID3V1, TagFormat.ID3V1_1, TagFormat.APEV1);
```

### Performance-Optimierung für große Dateiensammlungen
```java
// Adaptive Strategie basierend auf Dateigröße
public ScanConfiguration getOptimalConfig(String filePath) {
    File file = new File(filePath);
    long size = file.length();
    
    if (size < 1_000_000) { // < 1 MB
        return ScanConfiguration.fullScan(); // Geringer Overhead
    } else if (size < 100_000_000) { // < 100 MB  
        return ScanConfiguration.comfortScan(); // Ausgewogen
    } else {
        // Große Dateien: nur häufige Formate
        return ScanConfiguration.customScan(
            TagFormat.ID3V2_4, TagFormat.ID3V2_3, 
            TagFormat.VORBIS_COMMENT, TagFormat.MP4
        );
    }
}
```
## Fehlerbehandlung

### Robuste Fehlerbehandlung in allen Modi

```java
try {
    List<TagInfo> tags = TagFormatDetector.detectTagFormats(filePath, config);
} catch (IOException e) {
    // Dateizugriffsfehler
    logger.error("Cannot read file: " + e.getMessage());
}

// Batch-Verarbeitung mit Fehlertoleranz
Map<String, List<TagInfo>> results = TagFormatDetector.detectTagFormats(filePaths, config);
results.forEach((path, tags) -> {
    if (tags.isEmpty()) {
        logger.warn("No tags found in: " + path);
    } else {
        logger.info("Found {} tags in: {}", tags.size(), path);
    }
});
```

### Graceful Degradation

```java
// Fallback-Strategie bei Custom Scan Fehlern
public List<TagInfo> robustDetection(String filePath, List<TagFormat> preferredFormats) {
    try {
        // Versuche Custom Scan
        return TagFormatDetector.detectTagFormatsCustomScan(filePath, preferredFormats);
    } catch (Exception e) {
        logger.warn("Custom scan failed, falling back to comfort scan: " + e.getMessage());
        try {
            // Fallback zu Comfort Scan
            return TagFormatDetector.detectTagFormatsComfortScan(filePath);
        } catch (Exception e2) {
            logger.error("All scan modes failed: " + e2.getMessage());
            return Collections.emptyList();
        }
    }
}
```

## Unit Testing

### Umfassende Test-Suite für alle Modi

```java
@Test
void testAllScanModes() throws IOException {
    String testFile = "test.mp3";
    
    // Comfort Scan
    List<TagInfo> comfortTags = TagFormatDetector.detectTagFormatsComfortScan(testFile);
    assertNotNull(comfortTags);
    
    // Full Scan
    List<TagInfo> fullTags = TagFormatDetector.detectTagFormatsFullScan(testFile);
    assertNotNull(fullTags);
    assertTrue(fullTags.size() >= comfortTags.size()); // Full scan sollte mindestens so viele finden
    
    // Custom Scan
    List<TagInfo> customTags = TagFormatDetector.detectTagFormatsCustomScan(
        testFile, TagFormat.ID3V2_3, TagFormat.ID3V1);
    assertNotNull(customTags);
}
```
## Best Practices

### 1. Scan-Modus-Auswahl

```java
// ✅ Gut: Explizite Konfiguration
ScanConfiguration config = ScanConfiguration.comfortScan();

// ❌ Schlecht: Magic Boolean
detectTagFormats(filePath, false);
```

### 2. Batch-Verarbeitung

```java
// ✅ Gut: Batch-API verwenden
Map<String, List<TagInfo>> results = TagFormatDetector.detectTagFormats(filePaths, config);

// ❌ Schlecht: Schleife mit Einzelaufrufen
for (String path : filePaths) {
    detectTagFormats(path, config); // Kein Caching-Vorteil
}
```

### 3. Custom Scan Optimierung

```java
// ✅ Gut: Häufige Formate zuerst
ScanConfiguration.customScan(
    TagFormat.ID3V2_3, TagFormat.ID3V2_4, TagFormat.VORBIS_COMMENT
);

// ❌ Suboptimal: Seltene Formate zuerst
ScanConfiguration.customScan(
    TagFormat.LYRICS3V1, TagFormat.BWF_V0, TagFormat.ID3V2_3
);
```

### 4. Ressourcen-Management

```java
// ✅ Gut: Cache-Management bei langen Läufen
if (processedFiles > 10000) {
    TagFormatDetector.clearCache(); // Speicher freigeben
}

// ✅ Gut: Dateispezifische Cache-Bereinigung
TagFormatDetector.removeCacheEntry(updatedFilePath);
```

## Troubleshooting

### Häufige Probleme

**Problem:** Comfort Scan findet keine Tags in bekannter Datei
```java
// Lösung: Full Scan für Debugging verwenden
List<TagInfo> debugTags = TagFormatDetector.detectTagFormatsFullScan(filePath);
if (debugTags.isEmpty()) {
    logger.info("Datei enthält wirklich keine Tags");
} else {
    logger.warn("Comfort Scan hat Tags übersehen: {}", debugTags);
}
```

**Problem:** Custom Scan ist langsamer als erwartet
```java
// Lösung: Prioritäten prüfen und anpassen
List<TagFormat> optimizedFormats = Arrays.asList(
    TagFormat.ID3V2_3, // Häufigstes Format zuerst
    TagFormat.VORBIS_COMMENT,
    TagFormat.MP4
    // Seltene Formate weglassen
);
```

**Problem:** Memory-Probleme bei großen Sammlungen
```java
// Lösung: Streaming-Verarbeitung mit Cache-Management
int batchSize = 1000;
for (int i = 0; i < allFiles.size(); i += batchSize) {
    List<String> batch = allFiles.subList(i, Math.min(i + batchSize, allFiles.size()));
    processFiles(batch);
    
    if (i % (batchSize * 10) == 0) {
        TagFormatDetector.clearCache(); // Regelmäßige Cache-Bereinigung
        System.gc(); // Garbage Collection hint
    }
}
```

## Fazit

Die neuen Scan-Modi bieten eine flexible und performante Lösung für verschiedene Anwendungsszenarien:

- **Comfort Scan** für die meisten Anwendungen
- **Full Scan** für vollständige Analyse und Debugging
- **Custom Scan** für spezialisierte und performance-kritische Anwendungen

Die API bietet gleichzeitig moderne, explizite Konfigurationsmöglichkeiten.