# Tagix Schreibimplementierung — Detaillierter Implementierungsplan

## Gesamtarchitektur

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           TagWriter (Orchestrator)                           │
│                        WriteMode · WriteConfiguration                        │
├──────────────────────────────────────────────────────────────────────────────┤
│                        TagWritingStrategy (Interface)                        │
│               canWrite · writeTags · formatMetadata · metadataKeyFormat       │
│                ┌─────────────────────────────────────────────────┐           │
│                │       AbstractTagWritingStrategy (Basis)         │           │
│                │  copyExisting · mergeNew · validate · truncate   │           │
│                └─────────────────────────────────────────────────┘           │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌─────────┐ ┌─────────┐ ┌────────┐ │
│  │ID3Writing│ │VorbisWr. │ │APEWrit.  │ │MP4Write.│ │RIFFWrit.│ │  ...   │ │
│  │Strategy  │ │Strategy  │ │Strategy  │ │Strategy │ │Strategy │ │        │ │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬────┘ └────┬────┘ └────────┘ │
├───────┼─────────────┼────────────┼────────────┼───────────┼─────────────────┤
│       │             │            │            │           │                   │
│   SeekableDataSink (Interface)                                        │
│   ┌─────────────┐  ┌────────────────┐  ┌──────────────────┐             │
│   │ByteArraySink│  │FileChannelSink │  │  TempFileSink    │             │
│   │             │  │  (forPath)     │  │  (forTempFile)   │             │
│   └─────────────┘  └────────────────┘  │  commitTo()      │             │
│                                         └──────────────────┘             │
├───────────────────────────────────────────────────────────────────────────┤
│                SourceWriter · BinaryDataWriter (Hilfswerkzeuge)            │
└───────────────────────────────────────────────────────────────────────────┘
```

---

## Phase 1: IO-Schicht

### 1.1 — `SeekableDataSink` (Interface)

**Datei:** `src/main/java/com/schwanitz/io/SeekableDataSink.java`

```java
package com.schwanitz.io;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;

/**
 * Schreib-Schnittstelle für Byte-Arrays an beliebigen Positionen.
 * Gegenstück zu {@link SeekableDataSource} – erlaubt sequenzielles und
 * zufälliges Schreiben in eine Zieldatei.
 */
public interface SeekableDataSink extends Closeable, Flushable {

    /**
     * Schreibt Bytes an die angegebene Position.
     *
     * @param offset absolute Position in Bytes
     * @param data   zu schreibende Bytes
     */
    void write(long offset, byte[] data) throws IOException;

    /**
     * Aktuelle Größe der Zieldatei.
     */
    long length() throws IOException;

    /**
     * Setzt die Größe der Zieldatei.
     * Bei Verkürzung: Bytes ab der neuen Länge werden abgeschnitten.
     * Bei Verlängerung: Lücken werden mit Nullbytes gefüllt.
     */
    void setLength(long length) throws IOException;
}
```

### 1.2 — `ByteArraySink`

**Datei:** `src/main/java/com/schwanitz/io/ByteArraySink.java`

```java
package com.schwanitz.io;

import java.io.IOException;
import java.util.Arrays;

/**
 * In-Memory-Ziel für Schreibvorgänge.
 * Wird primär für Tests und In-Memory-Operationen verwendet.
 */
public class ByteArraySink implements SeekableDataSink {

    private byte[] buffer;
    private int size;

    public ByteArraySink(int initialCapacity) {
        this.buffer = new byte[initialCapacity];
        this.size = 0;
    }

    public ByteArraySink(byte[] initialData) {
        this.buffer = Arrays.copyOf(initialData, initialData.length);
        this.size = initialData.length;
    }

    @Override
    public void write(long offset, byte[] data) throws IOException {
        int pos = (int) offset;
        ensureCapacity(pos + data.length);
        System.arraycopy(data, 0, buffer, pos, data.length);
        if (pos + data.length > size) {
            size = pos + data.length;
        }
    }

    @Override
    public long length() {
        return size;
    }

    @Override
    public void setLength(long length) {
        this.size = (int) length;
    }

    /**
     * Gibt den aktuellen Inhalt als Byte-Array zurück.
     */
    public byte[] toByteArray() {
        return Arrays.copyOf(buffer, size);
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity > buffer.length) {
            int newCapacity = Math.max(buffer.length * 2, minCapacity);
            buffer = Arrays.copyOf(buffer, newCapacity);
        }
    }

    @Override
    public void close() { /* kein Cleanup nötig */ }

    @Override
    public void flush() { /* kein Cleanup nötig */ }
}
```

### 1.3 — `FileChannelSink` + `TempFileSink`

**Datei:** `src/main/java/com/schwanitz/io/SeekableDataSinks.java`

```java
package com.schwanitz.io;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.*;

/**
 * Factory für {@link SeekableDataSink}-Implementierungen.
 */
public final class SeekableDataSinks {

    private SeekableDataSinks() {}

    /**
     * Erzeugt einen Sink, der direkt in die angegebene Datei schreibt.
     */
    public static SeekableDataSink forPath(Path path) throws IOException {
        FileChannel channel = FileChannel.open(path,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
        return new FileChannelSink(channel);
    }

    /**
     * Erzeugt einen temporären Sink.
     * Nach Abschluss muss {@link TempFileSink#commitTo(Path)} aufgerufen werden.
     */
    public static TempFileSink forTempFile(String suffix) throws IOException {
        Path tempFile = Files.createTempFile("tagix-write-", suffix);
        FileChannel channel = FileChannel.open(tempFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
        return new TempFileSink(channel, tempFile);
    }

    /**
     * Erzeugt einen in-memory Sink (nützlich für Tests).
     */
    public static SeekableDataSink forBytes() {
        return new ByteArraySink(1024);
    }

    /**
     * Erzeugt einen in-memory Sink mit Startdaten.
     */
    public static SeekableDataSink forBytes(byte[] initialData) {
        return new ByteArraySink(initialData);
    }

    // =========================================================
    // Innere Klassen
    // =========================================================

    /**
     * Schreibt direkt in eine Datei über einen FileChannel.
     */
    static class FileChannelSink implements SeekableDataSink {

        private final FileChannel channel;

        FileChannelSink(FileChannel channel) {
            this.channel = channel;
        }

        @Override
        public void write(long offset, byte[] data) throws IOException {
            var buffer = java.nio.ByteBuffer.wrap(data);
            long written = 0;
            while (written < data.length) {
                written += channel.write(buffer, offset + written);
            }
        }

        @Override
        public long length() throws IOException {
            return channel.size();
        }

        @Override
        public void setLength(long length) throws IOException {
            channel.truncate(length);
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }

        @Override
        public void flush() throws IOException {
            channel.force(true);
        }
    }

    /**
     * Temporärer Sink mit Atomar-Commit.
     * Schreibt in eine Temporärdatei, die dann per
     * atomare Verschiebung an die Zieldatei übergeben wird.
     */
    public static class TempFileSink implements SeekableDataSink {

        private final FileChannel channel;
        private final Path tempPath;

        TempFileSink(FileChannel channel, Path tempPath) {
            this.channel = channel;
            this.tempPath = tempPath;
        }

        @Override
        public void write(long offset, byte[] data) throws IOException {
            var buffer = java.nio.ByteBuffer.wrap(data);
            long written = 0;
            while (written < data.length) {
                written += channel.write(buffer, offset + written);
            }
        }

        @Override
        public long length() throws IOException {
            return channel.size();
        }

        @Override
        public void setLength(long length) throws IOException {
            channel.truncate(length);
        }

        /**
         * Verschiebt die temporäre Datei atomar an die Zielposition.
         * Alte Zieldatei wird überschrieben.
         */
        public void commitTo(Path target) throws IOException {
            channel.force(true);
            channel.close();
            // Atomare Verschiebung auf demselben Dateisystem
            Files.move(tempPath, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        }

        @Override
        public void close() throws IOException {
            channel.close();
            Files.deleteIfExists(tempPath);
        }

        @Override
        public void flush() throws IOException {
            channel.force(true);
        }

        public Path getTempPath() {
            return tempPath;
        }
    }
}
```

### 1.4 — `SourceWriter`

**Datei:** `src/main/java/com/schwanitz/io/SourceWriter.java`

```java
package com.schwanitz.io;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Schreibt sequenziell in einen {@link SeekableDataSink}.
 * Ermöglicht das einfache Ersetzen von Datenblöcken durch Merken
 * der Startposition, Schreiben des neuen Inhalts und Abschneiden
 * überschüssiger alter Bytes.
 */
public class SourceWriter implements Closeable {

    private final SeekableDataSink sink;
    private long position;

    public SourceWriter(SeekableDataSink sink, long startPosition) {
        this.sink = sink;
        this.position = startPosition;
    }

    public long getPosition() {
        return position;
    }

    public long length() throws IOException {
        return sink.length();
    }

    public void seek(long newPosition) {
        this.position = newPosition;
    }

    public void skipBytes(int count) {
        this.position += count;
    }

    public void write(byte[] data) throws IOException {
        sink.write(position, data);
        position += data.length;
    }

    public void write(byte[] data, int offset, int length) throws IOException {
        byte[] slice = new byte[length];
        System.arraycopy(data, offset, slice, 0, length);
        write(slice);
    }

    public void writeByte(int value) throws IOException {
        write(new byte[]{(byte) (value & 0xFF)});
    }

    public void writeInt16BE(int value) throws IOException {
        write(new byte[]{
                (byte) ((value >> 8) & 0xFF),
                (byte) (value & 0xFF)
        });
    }

    public void writeInt32BE(int value) throws IOException {
        write(new byte[]{
                (byte) ((value >> 24) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) (value & 0xFF)
        });
    }

    public void writeInt32LE(int value) throws IOException {
        write(new byte[]{
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 24) & 0xFF)
        });
    }

    public void writeString(String text, Charset charset) throws IOException {
        write(text.getBytes(charset));
    }

    @Override
    public void close() throws IOException {
        sink.close();
    }
}
```

### 1.5 — `BinaryDataWriter`

**Datei:** `src/main/java/com/schwanitz/io/BinaryDataWriter.java`

```java
package com.schwanitz.io;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Statische Hilfsmethoden zum Schreiben von Binärdaten
 * in Byte-Arrays oder {@link SeekableDataSink}-Ziele.
 */
public final class BinaryDataWriter {

    private BinaryDataWriter() {}

    // =========================================================
    // Schreiboperationen direkt in byte[]
    // =========================================================

    public static void writeBigEndianInt16(byte[] target, int offset, int value) {
        target[offset]     = (byte) ((value >> 8) & 0xFF);
        target[offset + 1] = (byte) (value & 0xFF);
    }

    public static void writeBigEndianInt32(byte[] target, int offset, int value) {
        target[offset]     = (byte) ((value >> 24) & 0xFF);
        target[offset + 1] = (byte) ((value >> 16) & 0xFF);
        target[offset + 2] = (byte) ((value >> 8) & 0xFF);
        target[offset + 3] = (byte) (value & 0xFF);
    }

    public static void writeLittleEndianInt16(byte[] target, int offset, int value) {
        target[offset]     = (byte) (value & 0xFF);
        target[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    public static void writeLittleEndianInt32(byte[] target, int offset, int value) {
        target[offset]     = (byte) (value & 0xFF);
        target[offset + 1] = (byte) ((value >> 8) & 0xFF);
        target[offset + 2] = (byte) ((value >> 16) & 0xFF);
        target[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    /**
     * Schreibt einen 4-Byte Synchsafe-Integer (jedes Byte nur 7 Bit).
     * Wird in ID3v2 für Tag-Größen und Framesizes verwendet.
     */
    public static void writeSynchsafeInt(byte[] target, int offset, int value) {
        target[offset]     = (byte) ((value >> 21) & 0x7F);
        target[offset + 1] = (byte) ((value >> 14) & 0x7F);
        target[offset + 2] = (byte) ((value >> 7) & 0x7F);
        target[offset + 3] = (byte) (value & 0x7F);
    }

    // =========================================================
    // Schreiboperationen in SeekableDataSink (direkte Position)
    // =========================================================

    public static void writeBigEndianInt16(SeekableDataSink sink, long offset, int value) throws IOException {
        sink.write(offset, new byte[]{
                (byte) ((value >> 8) & 0xFF),
                (byte) (value & 0xFF)
        });
    }

    public static void writeBigEndianInt32(SeekableDataSink sink, long offset, int value) throws IOException {
        sink.write(offset, new byte[]{
                (byte) ((value >> 24) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) (value & 0xFF)
        });
    }

    public static void writeLittleEndianInt32(SeekableDataSink sink, long offset, int value) throws IOException {
        sink.write(offset, new byte[]{
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 24) & 0xFF)
        });
    }

    public static void writeSynchsafeInt(SeekableDataSink sink, long offset, int value) throws IOException {
        sink.write(offset, new byte[]{
                (byte) ((value >> 21) & 0x7F),
                (byte) ((value >> 14) & 0x7F),
                (byte) ((value >> 7) & 0x7F),
                (byte) (value & 0x7F)
        });
    }

    public static void writeString(SeekableDataSink sink, long offset, String text, Charset charset) throws IOException {
        sink.write(offset, text.getBytes(charset));
    }

    // =========================================================
    // Schreiboperationen über SourceWriter (sequenziell)
    // =========================================================

    public static void writeBigEndianInt16(SourceWriter writer, int value) throws IOException {
        writer.write(new byte[]{
                (byte) ((value >> 8) & 0xFF),
                (byte) (value & 0xFF)
        });
    }

    public static void writeBigEndianInt32(SourceWriter writer, int value) throws IOException {
        writer.write(new byte[]{
                (byte) ((value >> 24) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) (value & 0xFF)
        });
    }

    public static void writeLittleEndianInt32(SourceWriter writer, int value) throws IOException {
        writer.write(new byte[]{
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 24) & 0xFF)
        });
    }

    public static void writeSynchsafeInt(SourceWriter writer, int value) throws IOException {
        writer.write(new byte[]{
                (byte) ((value >> 21) & 0x7F),
                (byte) ((value >> 14) & 0x7F),
                (byte) ((value >> 7) & 0x7F),
                (byte) (value & 0x7F)
        });
    }

    public static void writeString(SourceWriter writer, String text, Charset charset) throws IOException {
        writer.write(text.getBytes(charset));
    }
}
```

---

## Phase 2: Writing-Framework

### 2.1 — `WriteMode` (Enum)

**Datei:** `src/main/java/com/schwanitz/tagging/WriteMode.java`

```java
package com.schwanitz.tagging;

/**
 * Definiert das Schreibverhalten.
 */
public enum WriteMode {

    /**
     * Erstellt neuen Tag-Header; überschreibt vorhandene Daten.
     * Die komplette Datei wird neu geschrieben.
     */
    CREATE_NEW,

    /**
     * Aktualisiert nur angegebene Felder.
     * Vorhandene Felder bleiben erhalten.
     */
    UPDATE_EXISTING,

    /**
     * Ersetzt den gesamten Metadaten-Bereich.
     * Alte Tags werden vollständig überschrieben.
     */
    REPLACE_ALL,

    /**
     * Entfernt den Metadaten-Bereich komplett.
     */
    REMOVE
}
```

### 2.2 — `WriteConfiguration`

**Datei:** `src/main/java/com/sschwanitz/tagging/WriteConfiguration.java`

```java
package com.schwanitz.tagging;

import com.schwanitz.api.AudioFormat;
import java.util.Map;

/**
 * Konfiguration für einen Schreibvorgang.
 *
 * @param mode        Schreibmodus
 * @param format      Zielformat
 * @param fields      Zu schreibende Felder (Key → Value)
 * @param id3Version  Bevorzugte ID3-Version (z.B. "2.4"); null = Standard
 */
public record WriteConfiguration(
        WriteMode mode,
        AudioFormat format,
        Map<String, String> fields,
        String id3Version
) {

    /**
     * Erzeugt eine UPDATE-Konfiguration mit den gegebenen Feldern.
     */
    public static WriteConfiguration update(AudioFormat format, Map<String, String> fields) {
        return new WriteConfiguration(WriteMode.UPDATE_EXISTING, format, fields, null);
    }

    /**
     * Erzeugt eine REPLACE-Konfiguration.
     */
    public static WriteConfiguration replace(AudioFormat format, Map<String, String> fields) {
        return new WriteConfiguration(WriteMode.REPLACE_ALL, format, fields, null);
    }

    /**
     * Erzeugt eine REMOVE-Konfiguration.
     */
    public static WriteConfiguration remove(AudioFormat format) {
        return new WriteConfiguration(WriteMode.REMOVE, format, Map.of(), null);
    }
}
```

### 2.3 — `WriteResult`

**Datei:** `src/main/java/com/schwanitz/tagging/WriteResult.java`

```java
package com.schwanitz.tagging;

import java.util.Collections;
import java.util.Map;

/**
 * Ergebnis eines Schreibvorgangs.
 *
 * @param success       Schreibvorgang erfolgreich?
 * @param mode          Verwendeter Schreibmodus
 * @param format        Geschriebenes Format
 * @param fieldsWritten Anzahl geschriebener Felder
 * @param details       Detaillierte Informationen (z.B. Fehlerhinweise)
 * @param writtenFields Die tatsächlich geschriebenen Felder
 */
public record WriteResult(
        boolean success,
        WriteMode mode,
        AudioFormat format,
        int fieldsWritten,
        Map<String, String> details,
        Map<String, String> writtenFields
) {

    public static WriteResult success(WriteMode mode, AudioFormat format,
                                       int fieldsWritten, Map<String, String> writtenFields) {
        return new WriteResult(true, mode, format, fieldsWritten,
                Map.of(), writtenFields);
    }

    public static WriteResult failure(WriteMode mode, AudioFormat format, String error) {
        return new WriteResult(false, mode, format, 0,
                Map.of("error", error), Map.of());
    }

    /**
     * Gibt die Fehlermeldung zurück, oder null bei Erfolg.
     */
    public String errorMessage() {
        return details.getOrDefault("error", null);
    }
}
```

### 2.4 — `TagWritingStrategy` (Interface)

**Datei:** `src/main/java/com/schwanitz/strategies/writing/context/TagWritingStrategy.java`

```java
package com.schwanitz.strategies.writing.context;

import com.schwanitz.api.AudioFormat;
import com.schwanitz.io.SeekableDataSink;
import com.schwanitz.tagging.WriteConfiguration;
import com.schwanitz.tagging.WriteResult;

/**
 * Strategie-Schnittstelle für das Schreiben von Metadaten
 * in ein bestimmtes Audioformat.
 */
public interface TagWritingStrategy {

    /**
     * Gibt an, ob diese Strategie das angegebene Format schreiben kann.
     */
    boolean canWrite(AudioFormat format);

    /**
     * Schreibt die Metadaten in die Zieldatei.
     *
     * @param configuration Schreibkonfiguration
     * @param source        Lesbare Quelldatei (bestehende Tags)
     * @param sink          Schreibbare Zieldatei
     * @return Ergebnis des Schreibvorgangs
     */
    WriteResult writeTags(WriteConfiguration configuration,
                          SeekableDataSource source,
                          SeekableDataSink sink) throws IOException;

    /**
     * Gibt die ID der Schreibstrategie zurück.
     */
    String strategyId();
}
```

### 2.5 — `AbstractTagWritingStrategy`

**Datei:** `src/main/java/com/schwanitz/strategies/writing/AbstractTagWritingStrategy.java`

```java
package com.schwanitz.strategies.writing;

import com.schwanitz.api.AudioFormat;
import com.schwanitz.io.SeekableDataSink;
import com.schwanitz.io.SourceWriter;
import com.schwanitz.tagging.WriteConfiguration;
import com.schwanitz.tagging.WriteMode;
import com.schwanitz.tagging.WriteResult;
import com.schwanitz.strategies.writing.context.TagWritingStrategy;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Basisklasse für Schreibstrategien.
 * Bietet gemeinsame Hilfsmethoden für alle Formate.
 */
public abstract class AbstractTagWritingStrategy implements TagWritingStrategy {

    /**
     * Kopiert den binären Inhalt aus der Quelldatei in den Sink.
     */
    protected void copyExistingData(SeekableDataSource source, SeekableDataSink sink,
                                    long sourceOffset, long length, long sinkOffset) throws IOException {
        byte[] data = source.readBytes(sourceOffset, (int) length);
        sink.write(sinkOffset, data);
    }

    /**
     * Erstellt eine Zusammenführung alter und neuer Felder.
     * Neue Felder überschreiben vorhandene.
     */
    protected Map<String, String> mergeFields(Map<String, String> existing,
                                               Map<String, String> newFields,
                                               WriteMode mode) {
        if (mode == WriteMode.REPLACE_ALL) {
            return new HashMap<>(newFields);
        }
        // UPDATE_EXISTING: Bestehende beibehalten, neue überschreiben
        Map<String, String> merged = new HashMap<>(existing);
        merged.putAll(newFields);
        return merged;
    }

    /**
     * Kürzt einen String auf die maximale Länge.
     */
    protected String truncate(String value, int maxLength) {
        if (value == null) return null;
        if (value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

    /**
     * Validiert die Eingaben eines Schreibvorgangs.
     * @return Fehlermeldung oder null bei Erfolg
     */
    protected String validateInput(WriteConfiguration config) {
        if (config == null) return "WriteConfiguration darf nicht null sein";
        if (config.format() == null) return "AudioFormat darf nicht null sein";
        if (config.fields() == null) return "Fields dürfen nicht null sein";
        if (config.mode() == null) return "WriteMode darf nicht null sein";
        if (config.mode() != WriteMode.REMOVE && config.fields().isEmpty()) {
            return "Bei Schreibmodus " + config.mode() + " müssen Felder angegeben werden";
        }
        return null;
    }

    /**
     * Erstellt ein Erfolgs-WriteResult.
     */
    protected WriteResult success(WriteConfiguration config, int count,
                                   Map<String, String> writtenFields) {
        return WriteResult.success(config.mode(), config.format(), count, writtenFields);
    }

    /**
     * Erstellt ein Fehler-WriteResult.
     */
    protected WriteResult failure(WriteConfiguration config, String error) {
        return WriteResult.failure(config.mode(), config.format(), error);
    }
}
```

### 2.6 — `TagWritingStrategyFactory`

**Datei:** `src/main/java/com/schwanitz/strategies/writing/factory/TagWritingStrategyFactory.java`

```java
package com.schwanitz.strategies.writing.factory;

import com.schwanitz.api.AudioFormat;
import com.schwanitz.strategies.writing.context.TagWritingStrategy;
import java.util.*;

/**
 * Registriert und liefert die passende Schreibstrategie
 * für ein gegebenes Audioformat.
 */
public class TagWritingStrategyFactory {

    private final Map<AudioFormat, TagWritingStrategy> strategies = new EnumMap<>(AudioFormat.class);

    /**
     * Registriert eine Schreibstrategie für alle zugehörigen Formate.
     */
    public void register(TagWritingStrategy strategy) {
        for (AudioFormat format : AudioFormat.values()) {
            if (strategy.canWrite(format)) {
                strategies.put(format, strategy);
            }
        }
    }

    /**
     * Liefert die Schreibstrategie für das Format oder null.
     */
    public Optional<TagWritingStrategy> getStrategy(AudioFormat format) {
        return Optional.ofNullable(strategies.get(format));
    }

    /**
     * Prüft, ob das Format beschreibbar ist.
     */
    public boolean isWritable(AudioFormat format) {
        return strategies.containsKey(format);
    }

    /**
     * Gibt alle unterstützten Formate zurück.
     */
    public Set<AudioFormat> supportedFormats() {
        return Collections.unmodifiableSet(strategies.keySet());
    }
}
```

---

## Phase 3: Format-Spezifische Strategien

### 3.1 — `ID3WritingStrategy`

**Datei:** `src/main/java/com/schwanitz/strategies/writing/ID3WritingStrategy.java`

```
Strategie für ID3v1, ID3v1.1, ID3v2.2, ID3v2.3, ID3v2.4

Algorithmen:
- ID3v1/1.1: Ersetze Bytes 0-125 direkt (128 Bytes Fixed-Length)
- ID3v2.3/2.4:
  1. Lies gesamten Dateiinhalt
  2. Identifiziere audioStart (nach ID3v2-Header oder 0 wenn keiner)
  3. Entferne alten ID3v2-Block + ID3v1 am Ende (falls vorhanden)
  4. Erstelle neuen ID3v2-Block mit allen Feldern
  5. Erstelle neuen ID3v1-Block (128 Bytes)
  6. Schreibe: Header(10) + Frames + Audio + ID3v1(128)

Frame-Schreiblogik (je nach Version):
  v2.3: FrameHeader(10) + DataSize(4 BE) + Flags(2) + Data
  v2.4: FrameHeader(10) + DataSize(4 Synchsafe) + Flags(2) + Data

Encoding-Unterstützung:
  v2.2: Encoding(1) + Text(UTF-16LE mit BOM)
  v2.3: Encoding(1) + Text(UTF-16LE mit BOM oder ISO-8859-1)
  v2.4: Encoding(1) + Text(UTF-8 oder UTF-16LE mit BOM)

Special Considerations:
- TRCK-Feld: "3/12" Format unterstützen
- TDRC vs TYER vs TDAT in v2.4 vs v2.3
- Kompatibilitätsflag für v2.3 → v2.4
```

### 3.2 — `VorbisWritingStrategy`

**Datei:** `src/main/java/com/schwanitz/strategies/writing/VorbisWritingStrategy.java`

```
Strategie für OGG Vorbis und FLAC Vorbis Comments

Algorithmen:
OGG Vorbis:
  1. Lies gesamten Dateiinhalt
  2. Finde alle OGG-Pakete (Page-Header寻址)
  3. Identifiziere Comments-Header (Packet Type 3 = Vorbis Comments)
  4. Ersetze Comments-Header-Paket komplett
  5. Korrigiere alle OGG-Sequenznummern (falls Paketlänge sich ändert)
  6. Aktualisiere alle OGG-Page-Checksums

FLAC Vorbis Comments:
  1. Lies gesamten Dateiinhalt
  2. Finde STREAMINFO-Block (erster Metadata-Block)
  3. Finde alle anderen Metadata-Blöcke
  4. Ersetze/erstelle VorbisComment-Block
  5. Aktualisiere Block-Flags (LAST_BLOCK) korrekt

Vorbis-Comment-Format:
  Vendor-String (Länge + UTF-8)
  Anzahl Felder (LE Int32)
  Für jedes Feld: "KEY=VALUE" (Länge + UTF-8)
```

### 3.3 — `APEWritingStrategy`

**Datei:** `src/main/java/com/schwanitz/strategies/writing/APEWritingStrategy.java`

```
Strategie für APEv2 Tags

Algorithmen:
  1. Lies gesamten Dateiinhalt
  2. Identifiziere APEv2-Header/Footer
  3. Bestimme Audio-Bereich (zwischen APEv2 und Daten)
  4. Erstelle neuen APEv2-Tag mit allen Feldern
  5. Schreibe: APEv2-Tag + Audio-Daten

APEv2-Header-Format (32 Bytes):
  "APETAGEX" (8 Bytes)
  Version (LE Int32, z.B. 2000)
  Item Count (LE Int32)
  Tag Size in Bytes (LE Int32, inkl. Header+Footer)
  Items (LE Int32)
  Reserved (8 Bytes)

APEv2-Item:
  Value Size (LE Int32, inkl. Null-Terminator)
  Flags (LE Int32, 1-2 Bits für Typ)
  Key (UTF-8, null-terminiert)
  Value (UTF-8, null-terminiert)

APEv2-Footer (gleich wie Header, aber Flags-Bit gesetzt)
```

### 3.4 — `MP4WritingStrategy`

**Datei:** `src/main/java/com/schwanitz/strategies/writing/MP4WritingStrategy.java`

```
Strategie für MP4/M4A iTunes-Style Tags (ilst)

Komplexe Baumstruktur:
moov → udta → meta → ilst → (item)

Algorithmen:
  1. Lies gesamten Dateiinhalt
  2. Parse die vollständige Atom-Struktur
  3. Finde moov/udta/meta/ilst
  4. Für jedes zu schreibende Feld:
     a. Erstelle Atom-Hierarchy: ©key → data
     b. data-Atom: Flags(4) + DataType(4) + Value
  5. Aktualisiere Größen aller Eltern-Atome
  6. Schreibe die modifizierte Datei

iTunes Data-Types:
  0x00000001: UTF-8
  0x00000002: UTF-16
  0x00000003: JPEG
  0x00000004: PNG

Wichtige ilst-Keys:
  ©nam, ©ART, ©alb, ©day, ©gen, ©cmt, trkn, disk, ©too
```

### 3.5 — `RIFFWritingStrategy`

**Datei:** `src/main/java/com/schwanitz/strategies/writing/RIFFWritingStrategy.java`

```
Strategie für RIFF/WAVE INFO-List + BWF bext

Algorithmen:
  1. Lies gesamten Dateiinhalt
  2. Finde LIST-INFO Chunk oder erstelle neuen
  3. Ersetze/ergänze INFO-Felder
  4. Aktualisiere Chunk-Größen in RIFF-Header
  5. Schreibe: RIFF-Header(12) + Chunks

INFO-Chunk-Format:
  "LIST" + Size(4) + "INFO"
  Für jedes Feld:
    Key(4) + Size(4) + Value (null-padded auf gerade Länge)

BWF bext-Chunk (falls zutreffend):
  608 Bytes fixed-length structure
```

### 3.6 — `ASFWritingStrategy`

**Datei:** `src/main/java/com/schwanitz/strategies/writing/ASFWritingStrategy.java`

```
Strategie für ASF/WMA Content Description Object

Algorithmen:
  1. Lies gesamten Dateiinhalt
  2. Parse die Header-Objektstruktur
  3. Finde Content Description Object (GUID: ...-4C24-B804-...)
  4. Ersetze/aktualisiere die Description-Felder
  5. Aktualisiere Header-Größe und Objekt-Größen
  6. Schreibe die modifizierte Datei

Content Description Object:
  ObjectID (GUID, 16 Bytes)
  ObjectSize (LE Int64)
  TitleLength (LE Int16)
  AuthorLength (LE Int16)
  CopyrightLength (LE Int16)
  DescriptionLength (LE Int16)
  RatingLength (LE Int16)
  Title (UTF-16LE, TitleLength Bytes)
  Author (UTF-16LE)
  Copyright (UTF-16LE)
  Description (UTF-16LE)
  Rating (UTF-16LE)
```

### 3.7 — `AIFFWritingStrategy`

**Datei:** `src/main/java/com/schwanitz/strategies/writing/AIFFWritingStrategy.java`

```
Strategie für AIFF IFF-Chunk-basierte Metadaten

Unterstützte Chunks:
- NAME: Name (pascal-string)
- AUTH: Author (pascal-string)
- (c) : Copyright (pascal-string)
- ANNO: Annotation (pascal-string)

Algorithmen:
  1. Lies gesamten Dateiinhalt
  2. Parse Chunk-Struktur (IFF-Format: ID + Size + Data)
  3. Ersetze/ergänze NAME/AUTH/ANNO/(c)-Chunks
  4. Aktualisiere FORM-AIFF Header-Größe
  5. Schreibe: FORM(12) + Chunks

Pascal-String-Format:
  Length(1 Byte) + Characters + Padding (gerade Länge)
```

### 3.8 — `MatroskaWritingStrategy`

**Datei:** `src/main/java/com/schwanitz/strategies/writing/MatroskaWritingStrategy.java`

```
Strategie für Matroska/WebM EBML-basierte Tags

Strategie: Vereinfachtes Replace-Only

Algorithmen:
  1. Lies gesamten Dateiinhalt
  2. Parse EBML-Header und Segment
  3. Identifiziere/erstelle Tags-Element
  4. Ersetze alle SimpleTag-Einträge
  5. Aktualisiere EBML-Segment-Größen
  6. Schreibe die modifizierte Datei

EBML-Element-Struktur:
  ElementID (1-4 Bytes VLE)
  ElementSize (1-8 Bytes VLE)
  ElementData

Tags → Tag → SimpleTag → TagName + TagString
```

### 3.9 — `DSFWritingStrategy` und `DFFWritingStrategy`

**Datei:** `src/main/java/com/schwanitz/strategies/writing/DSFWritingStrategy.java`  
**Datei:** `src/main/java/com/schwanitz/strategies/writing/DFFWritingStrategy.java`

```
Strategie für DSF und DFF

DSF (DSD Stream File):
  - Struktur: DSD_ 0x00000000 + Data-Chunk
  - DSF nutzt ID3v2 am Dateianfang (vor DSD-Header)
  - DSF-Konvertierung: 16 Bytes + ID3v2-Block + DSD-Header + Audio
  - Implementierung delegiert an ID3WritingStrategy

DFF (DSD Interchange File):
  - Struktur: FORM + FVER + MLST + DSTD + DSD_ + DATA
  - DFF nutzt ID3v2 am Dateianfang
  - Implementierung delegiert an ID3WritingStrategy
```

### 3.10 — `WavPackWritingStrategy`

**Datei:** `src/main/java/com/schwanitz/strategies/writing/WavPackWritingStrategy.java`

```
Strategie für WavPack native Tags (non-ID3)

WavPack-Header:
  "wvpk" (4 Bytes)
  Block Size (LE Int32)
  Version (LE Int16)
  ...

Tag-Block (in einem WavPack-Block):
  1. Lies den gesamten WavPack-Block
  2. Identifiziere Tag-Entry (Index 1 oder 3)
  3. Ersetze/aktualisiere Tag-Inhalt
  4. Aktualisiere Block-Größe und Prüfsumme
  5. Schreibe den modifizierten Block

WavPack-Tag-Format:
  Field Count (LE Int16)
  Für jedes Feld:
    Key (UTF-8, null-terminiert)
    Value (UTF-8, null-terminiert)
```

### 3.11 — `Lyrics3WritingStrategy`

**Datei:** `src/main/java/com/schwanitz/strategies/writing/Lyrics3WritingStrategy.java`

```
Strategie für Lyrics3v1 und Lyrics3v2

Lyrics3v2 (letzte 10 Bytes): "LYRICS200"
  → Suche nach "LYRICSBEGIN" in den letzten 10.010 Bytes
  → Key/Value-Paare: "TAG=[Key][Value][Length padded with spaces]"

Lyrics3v2 (wenn vorhanden):
  1. Lies die letzten Bytes
  2. Identifiziere Lyrics3-Block
  3. Ersetze/erstelle Lyrics-Felder
  4. Aktualisiere Längenangaben
  5. Schreibe den modifizierten Block

Unterstützte Felder:
  LYR: Lyrics
  AUT: Author
  ALB: Album
  TIT: Title
  ...
```

---

## Phase 4: Orchestrator

### 4.1 — `TagWriter`

**Datei:** `src/main/java/com/schwanitz/api/TagWriter.java`

```java
package com.schwanitz.api;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.io.SeekableDataSink;
import com.schwanitz.io.SeekableDataSinks;
import com.schwanitz.strategies.writing.context.TagWritingStrategy;
import com.schwanitz.strategies.writing.factory.TagWritingStrategyFactory;
import com.schwanitz.tagging.WriteConfiguration;
import com.schwanitz.tagging.WriteMode;
import com.schwanitz.tagging.WriteResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Zentraler Orchestrator für Schreibvorgänge.
 * Koordiniert Strategy-Auswahl, Datenfluss und Temporärdatei-Management.
 */
public class TagWriter {

    private final TagWritingStrategyFactory factory;

    public TagWriter(TagWritingStrategyFactory factory) {
        this.factory = factory;
    }

    /**
     * Schreibt Metadaten in eine Datei (Ersetzen/Update).
     *
     * @param configuration Schreibkonfiguration
     * @param source        Quelldatei (lesbar)
     * @param sink          Zieldatei (beschreibbar)
     * @return Ergebnis
     */
    public WriteResult writeTags(WriteConfiguration configuration,
                                  SeekableDataSource source,
                                  SeekableDataSink sink) throws IOException {

        AudioFormat format = configuration.format();

        Optional<TagWritingStrategy> strategyOpt = factory.getStrategy(format);
        if (strategyOpt.isEmpty()) {
            return WriteResult.failure(configuration.mode(), format,
                    "Keine Schreibstrategie für Format: " + format);
        }

        return strategyOpt.get().writeTags(configuration, source, sink);
    }

    /**
     * Bequemer Einzel-Feld-Update.
     */
    public WriteResult updateField(SeekableDataSource source,
                                    SeekableDataSink sink,
                                    AudioFormat format,
                                    String key, String value) throws IOException {
        WriteConfiguration config = WriteConfiguration.update(format, Map.of(key, value));
        return writeTags(config, source, sink);
    }

    /**
     * Bequeme Methode zum Entfernen aller Tags.
     */
    public WriteResult removeTags(SeekableDataSource source,
                                   SeekableDataSink sink,
                                   AudioFormat format) throws IOException {
        WriteConfiguration config = WriteConfiguration.remove(format);
        return writeTags(config, source, sink);
    }

    /**
     * Schreibt Tags in eine Datei auf dem Dateisystem.
     * Verwendet Temporärdatei + Atomare Verschiebung.
     */
    public WriteResult writeToFile(WriteConfiguration config, Path sourcePath) throws IOException {
        Path tempFile = sourcePath.resolveSibling(sourcePath.getFileName() + ".tmp");

        try (SeekableDataSource source = com.schwanitz.io.SeekableDataSources.forPath(sourcePath);
             SeekableDataSinks.TempFileSink sink = SeekableDataSinks.forTempFile(getExtension(sourcePath))) {

            WriteResult result = writeTags(config, source, sink);
            if (result.success()) {
                sink.commitTo(tempFile);
                java.nio.file.Files.move(tempFile, sourcePath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return result;
        } catch (Exception e) {
            java.nio.file.Files.deleteIfExists(tempFile);
            throw e;
        }
    }

    private String getExtension(Path path) {
        String name = path.getFileName().toString();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex >= 0 ? name.substring(dotIndex) : "";
    }
}
```

---

## Phase 5: Tests

### 5.1 — IO-Layer-Tests

**`src/test/java/com/schwanitz/io/SeekableDataSinksTest.java`**
- ByteArraySink: Schreiben, Lesen, Länge
- FileChannelSink: Schreiben in Temp-Datei, Flush
- TempFileSink: Commit auf Ziel-Datei, Aufräumen nach close
- Factory: forBytes, forTempFile

**`src/test/java/com/schwanitz/io/SourceWriterTest.java`**
- Sequenzielles Schreiben, Position-Tracking
- Seeking, Skipping
- Int16BE, Int32BE, Int32LE Schreibvorgänge
- String-Schreibvorgänge

**`src/test/java/com/schwanitz/io/BinaryDataWriterTest.java`**
- Alle statischen Methoden (Array- und Sink-Varianten)
- Synchsafe-Integer Schreibvorgänge
- Große/Werte-Grenzen

### 5.2 — Config-Tests

**`src/test/java/com/schwanitz/tagging/WriteConfigurationTest.java`**
- Record-Instantiierung
- Factory-Methoden (update, replace, remove)
- Null-Werte

**`src/test/java/com/schwanitz/tagging/WriteResultTest.java`**
- success() Factory
- failure() Factory
- errorMessage()

### 5.3 — Writing-Strategy-Tests

**`src/test/java/com/schwanitz/strategies/writing/unit/ID3WritingStrategyTest.java`**
- ID3v1: 128-Byte-Block wird korrekt geschrieben
- ID3v1.1: Comment-Split (30 → 28 + 1 + Genre)
- ID3v2.3: Frame-Größe (BigEndian)
- ID3v2.4: Frame-Größe (Synchsafe)
- canWrite(): true für MP3/AIFF/DSF/DFF

**`src/test/java/com/schwanitz/strategies/writing/unit/VorbisWritingStrategyTest.java`**
- OGG: Comments-Header-Ersetzung
- FLAC: VorbisComment-Block-Erstellung
- canWrite(): true für OGG/FLAC

**`src/test/java/com/schwanitz/strategies/writing/unit/APEWritingStrategyTest.java`**
- Header/Footer-Schreibvorgänge
- canWrite(): true für APE/APE-Monkey/APE-Musepack

**`src/test/java/com/schwanitz/strategies/writing/unit/MP4WritingStrategyTest.java`**
- iTunes ilst Atome-Erstellung
- canWrite(): true für M4A/MP4

**`src/test/java/com/schwanitz/strategies/writing/unit/RIFFWritingStrategyTest.java`**
- LIST-INFO Chunk-Erstellung
- canWrite(): true für WAV/WV/DFF

**`src/test/java/com/schwanitz/strategies/writing/unit/ASFWritingStrategyTest.java`**
- Content Description Object
- canWrite(): true für ASF/WMA

**`src/test/java/com/schwanitz/strategies/writing/unit/AIFFWritingStrategyTest.java`**
- NAME/AUTH/ANNO Chunks
- canWrite(): true für AIFF

**`src/test/java/com/schwanitz/strategies/writing/unit/MatroskaWritingStrategyTest.java`**
- EBML Tags
- canWrite(): true für MKV/WebM

**`src/test/java/com/schwanitz/strategies/writing/unit/DSFWritingStrategyTest.java`**
- Delegation an ID3WritingStrategy
- canWrite(): true für DSF

**`src/test/java/com/schwanitz/strategies/writing/unit/DFFWritingStrategyTest.java`**
- Delegation an ID3WritingStrategy
- canWrite(): true für DFF

**`src/test/java/com/schwanitz/strategies/writing/unit/WavPackWritingStrategyTest.java`**
- Native Tags
- canWrite(): true für WavPack

**`src/test/java/com/schwanitz/strategies/writing/unit/Lyrics3WritingStrategyTest.java`**
- Lyrics3v2 Schreibvorgänge
- canWrite(): true für MP3 (mit Lyrics3)

### 5.4 — Integration-Test

**`src/test/java/com/schwanitz/strategies/writing/integration/WriteRoundtripTest.java`**

```java
@Test
void id3v2_4_roundtrip() {
    // 1. Erstelle Test-MP3 mit ParsingStrategy
    // 2. Verändere Metadaten
    // 3. Schreibe mit TagWriter
    // 4. Lese mit ParsingStrategy
    // 5. Verifiziere Änderungen
}
```

### 5.5 — Factory-Test

**`src/test/java/com/schwanitz/strategies/writing/factory/TagWritingStrategyFactoryTest.java`**
- Alle Formate korrekt zugeordnet
- isWritable für unterstützte Formate
- isWritable für nicht unterstützte Formate

---

## Implementierungsreihenfolge (Reihenfolge beachten!)

```
[1] SeekableDataSink.java              ← Grundlage
[2] ByteArraySink.java                 ← Grundlage
[3] SeekableDataSinks.java             ← Factory
[4] SourceWriter.java                   ← Grundlage
[5] BinaryDataWriter.java              ← Grundlage
[6] WriteMode.java                      ← Konfiguration
[7] WriteConfiguration.java             ← Konfiguration
[8] WriteResult.java                    ← Konfiguration
[9] TagWritingStrategy.java            ← Interface
[10] AbstractTagWritingStrategy.java    ← Basis-Klasse
[11] TagWritingStrategyFactory.java     ← Factory
[12] ID3WritingStrategy.java           ← MVP
[13] VorbisWritingStrategy.java        ← MVP
[14] APEWritingStrategy.java           ← MVP
[15] TagWriter.java                     ← Orchestrator
[16] MP4WritingStrategy.java           ← Erweitert
[17] RIFFWritingStrategy.java          ← Erweitert
[18] ASFWritingStrategy.java           ← Erweitert
[19] AIFFWritingStrategy.java          ← Erweitert
[20] MatroskaWritingStrategy.java      ← Erweitert
[21] DSFWritingStrategy.java           ← Erweitert
[22] DFFWritingStrategy.java           ← Erweitert
[23] WavPackWritingStrategy.java       ← Erweitert
[24] Lyrics3WritingStrategy.java       ← Erweitert
[25] Alle Tests schreiben
[26] mvn compile && mvn test
```

---

## Zusammenfassung: Neue Dateien

| # | Datei | Phase |
|---|-------|-------|
| 1 | `io/SeekableDataSink.java` | IO |
| 2 | `io/ByteArraySink.java` | IO |
| 3 | `io/SeekableDataSinks.java` | IO |
| 4 | `io/SourceWriter.java` | IO |
| 5 | `io/BinaryDataWriter.java` | IO |
| 6 | `tagging/WriteMode.java` | Config |
| 7 | `tagging/WriteConfiguration.java` | Config |
| 8 | `tagging/WriteResult.java` | Config |
| 9 | `strategies/writing/context/TagWritingStrategy.java` | Framework |
| 10 | `strategies/writing/AbstractTagWritingStrategy.java` | Framework |
| 11 | `strategies/writing/factory/TagWritingStrategyFactory.java` | Framework |
| 12 | `strategies/writing/ID3WritingStrategy.java` | MVP |
| 13 | `strategies/writing/VorbisWritingStrategy.java` | MVP |
| 14 | `strategies/writing/APEWritingStrategy.java` | MVP |
| 15 | `api/TagWriter.java` | Orchestrator |
| 16 | `strategies/writing/MP4WritingStrategy.java` | Erweitert |
| 17 | `strategies/writing/RIFFWritingStrategy.java` | Erweitert |
| 18 | `strategies/writing/ASFWritingStrategy.java` | Erweitert |
| 19 | `strategies/writing/AIFFWritingStrategy.java` | Erweitert |
| 20 | `strategies/writing/MatroskaWritingStrategy.java` | Erweitert |
| 21 | `strategies/writing/DSFWritingStrategy.java` | Erweitert |
| 22 | `strategies/writing/DFFWritingStrategy.java` | Erweitert |
| 23 | `strategies/writing/WavPackWritingStrategy.java` | Erweitert |
| 24 | `strategies/writing/Lyrics3WritingStrategy.java` | Erweitert |

**Bestehende geänderte Dateien:**
| Datei | Änderung |
|-------|----------|
| `api/MetadataManager.java` | `TagWriter`-Referenz hinzufügen |
| `tagging/AudioFileTagger.java` | `TagWriter`-Integration |
| `strategies/parsing/context/ParsingStrategy.java` | Export für SeekableDataSource |
| `io/SeekableDataSources.java` | Export für Factory-Methode |
