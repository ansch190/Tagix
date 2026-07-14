package com.schwanitz.strategies.writing.context;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.interfaces.Metadata;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;
import com.schwanitz.tagging.WriteConfiguration;
import com.schwanitz.tagging.WriteResult;

import java.io.IOException;
import java.util.List;

/**
 * Strategie-Schnittstelle für das Schreiben von Audio-Metadaten-Tags.
 * <p>
 * Dieses Interface implementiert das Strategie-Muster für Tag-Schreibvorgänge.
 * Jedes unterstützte Audio-Tag-Format wird durch eine eigene konkrete Implementierung
 * repräsentiert, die festlegt, wie Metadaten in das jeweilige Format serialisiert
 * und in die Audiodatei geschrieben werden.
 * </p>
 * <p>
 * Die Auswahl der korrekten Strategie erfolgt über
 * {@link com.schwanitz.strategies.writing.factory.TagWritingStrategyFactory}.
 * </p>
 *
 * @see com.schwanitz.strategies.writing.factory.TagWritingStrategyFactory
 * @see TagFormat
 * @see Metadata
 */
public interface TagWritingStrategy {

    /**
     * Schreibt Metadaten in die angegebene Datenquelle.
     *
     * @param format     das Tag-Format, das geschrieben werden soll
     * @param metadata   die zu schreibenden Metadaten
     * @param source     die bestehende Audiodaten-Quelle
     * @param existingTag die Information über das bestehende Tag (kann {@code null} sein)
     * @param config     die Schreib-Konfiguration
     * @return das Ergebnis des Schreibvorgangs
     * @throws IOException bei Ein-/Ausgabefehlern
     */
    WriteResult writeTag(TagFormat format, Metadata metadata,
                         SeekableDataSource source, TagInfo existingTag,
                         WriteConfiguration config) throws IOException;

    /**
     * Gibt die von dieser Strategie unterstützten Tag-Formate zum Schreiben zurück.
     *
     * @return eine Liste der unterstützten {@link TagFormat}-Werte
     */
    List<TagFormat> getSupportedWriteFormats();

    /**
     * Prüft, ob dieses Format In-Place-Schreiben unterstützt.
     * <p>
     * In-Place-Schreiben ist möglich, wenn das neue Tag exakt an die Stelle
     * des alten Tags geschrieben werden kann, ohne dass die restliche Datei
     * verschoben werden muss. Typischerweise nur bei festen Größen möglich.
     * </p>
     *
     * @param format das zu prüfende Format
     * @return {@code true}, wenn In-Place-Schreiben möglich ist
     */
    boolean supportsInPlaceWrite(TagFormat format);
}
