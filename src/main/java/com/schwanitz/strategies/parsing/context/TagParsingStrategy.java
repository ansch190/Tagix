package com.schwanitz.strategies.parsing.context;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.tagging.TagFormat;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Strategie-Schnittstelle für das Parsen von Audio-Metadaten-Tags.
 *
 * <p>Dieses Interface implementiert das Strategie-Muster (Strategy Pattern) zur Entkopplung
 * der Tag-Parsing-Logik vom aufrufenden Kontext. Jedes unterstützte Audio-Tag-Format
 * wird durch eine eigene konkrete Implementierung repräsentiert, die festlegt, wie das
 * jeweilige Format gelesen und in {@link Metadata}-Objekte überführt wird.</p>
 *
 * <p>Die Auswahl der korrekten Strategie erfolgt über {@link com.schwanitz.strategies.parsing.factory.TagParsingStrategyFactory},
 * welche eine {@link TagFormat}-Enumeration auf die zugehörige {@code TagParsingStrategy}-Implementierung abbildet.</p>
 *
 * @see com.schwanitz.strategies.parsing.factory.TagParsingStrategyFactory
 * @see TagFormat
 * @see Metadata
 */
public interface TagParsingStrategy {

    /**
     * Prüft, ob diese Strategie das angegebene Tag-Format verarbeiten kann.
     *
     * @param format das zu prüfende Tag-Format
     * @return {@code true}, wenn diese Strategie das Format unterstützt, sonst {@code false}
     */
    boolean canHandle(TagFormat format);

    /**
     * Parst das Tag am angegebenen Offset in der Datei und liefert die extrahierten Metadaten.
     *
     * @param format das Tag-Format, das gelesen werden soll
     * @param file   die {@link RandomAccessFile}, aus der gelesen wird
     * @param offset der Start-Offset des Tags in der Datei
     * @param size   die Größe des Tags in Bytes
     * @return die extrahierten {@link Metadata}
     * @throws IOException wenn ein I/O-Fehler beim Lesen auftritt oder das Tag-Format ungültig ist
     */
    Metadata parseTag(TagFormat format, RandomAccessFile file, long offset, long size) throws IOException;
}