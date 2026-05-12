package com.schwanitz.strategies.detection.context;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Abstrakte Basisklasse für Tag-Erkennungsstrategien.
 * <p>
 * Diese Klasse implementiert das Schablonenmethode-Muster (Template Method Pattern)
 * für die Erkennung von Audio-Tags. Der Erkennungsprozess folgt einem festen
 * zweiphasigen Ablauf, der von der {@link FormatDetectionContext}-Klasse gesteuert wird:
 * <ol>
 *   <li><b>Phase 1 – Schnelle Signaturerkennung:</b> Über {@link #canDetect(byte[], byte[])}
 *       wird anhand von Pufferdaten aus dem Dateianfang und -ende geprüft, ob die
 *       Strategie für die jeweilige Datei zuständig ist. Diese Methode ist ressourcenschonend,
 *       da sie keine Dateizugriffe benötigt.</li>
 *   <li><b>Phase 2 – Detaillierte Tag-Analyse:</b> Wenn die Signaturerkennung erfolgreich ist,
 *       wird {@link #detectTags(SeekableDataSource, byte[], byte[])} aufgerufen, um die
 *       genauen Positionen und Größen der Tags zu ermitteln. Diese Methode führt Lesezugriffe
 *       auf der Datenquelle durch und ist daher aufwendiger.</li>
 * </ol>
 * <p>
 * Jede konkrete Strategie implementiert die Erkennung für ein oder mehrere bestimmte
 * Tag-Formate (z. B. ID3v2, APE, Vorbis Comment). Die Trennung in zwei Phasen
 * ermöglicht ein effizientes Ausschlussverfahren: nur Strategien, deren Signatur
 * übereinstimmt, führen die aufwendige Detailanalyse durch.
 *
 * @see FormatDetectionContext
 */
public abstract class TagDetectionStrategy {

    protected final Logger LOG = LoggerFactory.getLogger(getClass());

    /**
     * Gibt die von dieser Strategie unterstützten Tag-Formate zurück.
     *
     * @return eine unveränderliche Liste der unterstützten {@link TagFormat}-Werte
     */
    public abstract List<TagFormat> getSupportedTagFormats();

    /**
     * Prüft anhand von Dateipufferdaten schnell, ob diese Strategie für die
     * vorliegende Datei zuständig ist.
     * <p>
     * Diese Methode wird vor {@link #detectTags} aufgerufen und soll eine
     * ressourcenschonende Vorauswahl ermöglichen, ohne Dateizugriffe zu benötigen.
     *
     * @param startBuffer Puffer mit den ersten Bytes der Datei (typischerweise 4 KB)
     * @param endBuffer   Puffer mit den letzten Bytes der Datei (typischerweise 4 KB)
     * @return {@code true}, wenn die Signatur auf ein unterstütztes Tag-Format hinweist
     */
    public abstract boolean canDetect(byte[] startBuffer, byte[] endBuffer);

    /**
     * Führt die detaillierte Tag-Analyse durch und ermittelt die genauen Positionen
     * und Größen aller erkannten Tags in der Datenquelle.
     * <p>
     * Diese Methode wird nur aufgerufen, wenn {@link #canDetect} {@code true} zurückgegeben hat.
     * Sie führt Lesezugriffe auf der Datenquelle durch, um die genauen Offset- und Größeninformationen
     * der Tags zu bestimmen.
     *
     * @param source      die {@link SeekableDataSource} für Lesezugriffe
     * @param startBuffer Puffer mit den ersten Bytes der Datei
     * @param endBuffer   Puffer mit den letzten Bytes der Datei
     * @return eine Liste der erkannten {@link TagInfo}-Objekte; leer, wenn keine Tags gefunden wurden
     * @throws IOException wenn ein Fehler beim Lesen der Datenquelle auftritt
     */
    public abstract List<TagInfo> detectTags(SeekableDataSource source, byte[] startBuffer, byte[] endBuffer) throws IOException;
}