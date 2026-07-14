package com.schwanitz.strategies.writing;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.io.SeekableDataSink;
import com.schwanitz.io.SourceWriter;
import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.strategies.parsing.factory.TagParsingStrategyFactory;
import com.schwanitz.strategies.writing.context.TagWritingStrategy;
import com.schwanitz.tagging.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Abstrakte Basisklasse für alle Tag-Schreib-Strategien.
 * <p>
 * Kapselt die gemeinsame Logik für Schreibvorgänge:
 * <ul>
 *   <li>Metadaten-Mergen bei UPDATE_EXISTING-Modus</li>
 *   <li>Lesen bestehender Tags</li>
 *   <li>Audiodaten kopieren zwischen altem und neuem Tag</li>
 *   <li>Validierung der Eingaben</li>
 * </ul>
 * </p>
 *
 * @see TagWritingStrategy
 */
public abstract class AbstractTagWritingStrategy implements TagWritingStrategy {

    protected final Logger LOG = LoggerFactory.getLogger(getClass());

    private final String strategyName;
    private final TagParsingStrategyFactory parsingFactory;

    /**
     * Erzeugt eine neue Schreib-Strategie.
     *
     * @param strategyName der Name für Log-Ausgaben (z.B. "ID3", "Vorbis")
     * @param parsingFactory die Factory zum Lesen bestehender Tags
     */
    protected AbstractTagWritingStrategy(String strategyName, TagParsingStrategyFactory parsingFactory) {
        this.strategyName = strategyName;
        this.parsingFactory = parsingFactory;
    }

    /**
     * Gibt den Namen dieser Strategie zurück.
     *
     * @return der Strategie-Name
     */
    protected String getStrategyName() {
        return strategyName;
    }

    /**
     * Gibt die Parsing-Factory zurück.
     *
     * @return die Parsing-Factory
     */
    protected TagParsingStrategyFactory getParsingFactory() {
        return parsingFactory;
    }

    /**
     * Liest bestehende Metadaten aus einer Datenquelle für das angegebene Format.
     *
     * @param source  die Datenquelle
     * @param tagInfo die Information über das bestehende Tag
     * @return die gelesenen Metadaten oder {@code null} wenn kein Tag vorhanden
     */
    protected Metadata readExistingMetadata(SeekableDataSource source, TagInfo tagInfo) {
        if (tagInfo == null) {
            return null;
        }
        try {
            TagParsingStrategy parser = parsingFactory.getStrategyForFormat(tagInfo.getFormat());
            if (parser != null) {
                return parser.parseTag(tagInfo.getFormat(), source, tagInfo.getOffset(), tagInfo.getSize());
            }
        } catch (Exception e) {
            LOG.warn("Fehler beim Lesen des bestehenden {}-Tags", strategyName, e);
        }
        return null;
    }

    /**
     * Führt Metadaten im UPDATE-Modus zusammen.
     * <p>
     * Bestehende Felder werden durch neue ersetzt. Neue Felder werden hinzugefügt.
     * Felder, die nur im bestehenden Tag vorhanden sind, bleiben erhalten.
     * </p>
     *
     * @param existing das bestehende Metadata-Objekt
     * @param newMeta  die neuen Metadaten
     * @return die zusammengeführten Metadaten
     */
    protected Metadata mergeMetadata(Metadata existing, Metadata newMeta) {
        if (existing == null) {
            return newMeta;
        }
        if (newMeta == null || newMeta.getFields().isEmpty()) {
            return existing;
        }

        TagFormat format = null;
        for (TagFormat f : TagFormat.values()) {
            if (f.getFormatName().equals(existing.getTagFormat())) {
                format = f;
                break;
            }
        }
        GenericMetadata merged = new GenericMetadata(format);

        // Bestehende Felder kopieren
        for (MetadataField<?> field : existing.getFields()) {
            merged.addField(field);
        }

        // Neue Felder hinzufügen (ersetzt bestehende mit gleichem Key)
        for (MetadataField<?> field : newMeta.getFields()) {
            merged.removeField(field.getKey());
            merged.addField(field);
        }

        return merged;
    }

    /**
     * Kopiert Audiodaten von einer Quelle in ein Ziel, die nach dem alten Tag beginnen.
     *
     * @param source         die Quelldatenquelle
     * @param sink           das Schreibziel
     * @param audioStart     die Startposition der Audiodaten in der Quelle
     * @param writeTarget    die Startposition im Schreibziel
     * @param bufferSize     die Puffergröße für das Kopieren
     * @return die Anzahl der kopierten Bytes
     * @throws IOException bei Ein-/Ausgabefehlern
     */
    protected long copyAudioData(SeekableDataSource source, SeekableDataSink sink,
                                  long audioStart, long writeTarget, int bufferSize) throws IOException {
        long sourceLength = source.length();
        if (audioStart >= sourceLength) {
            return 0;
        }

        long totalCopied = 0;
        byte[] buffer = new byte[bufferSize];
        long sourcePos = audioStart;
        long sinkPos = writeTarget;

        while (sourcePos < sourceLength) {
            int toRead = (int) Math.min(bufferSize, sourceLength - sourcePos);
            int bytesRead = source.read(sourcePos, buffer, 0, toRead);
            if (bytesRead <= 0) {
                break;
            }
            sink.write(sinkPos, buffer, 0, bytesRead);
            totalCopied += bytesRead;
            sourcePos += bytesRead;
            sinkPos += bytesRead;
        }

        return totalCopied;
    }

    /**
     * Validiert, dass Metadaten und Quelle nicht null sind.
     *
     * @param metadata die zu validierenden Metadaten
     * @param source   die zu validierende Quelle
     * @param format   das Tag-Format
     * @return ein Fehlerergebnis bei ungültigen Eingaben, {@code null} bei Erfolg
     */
    protected WriteResult validateInput(Metadata metadata, SeekableDataSource source, TagFormat format) {
        Objects.requireNonNull(metadata, "metadata must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(format, "format must not be null");
        return null;
    }
}
