package com.schwanitz.tagging;

import java.util.Objects;

/**
 * Repräsentiert ein erkanntes Audio-Tag mit Format, Position und Größe.
 * <p>
 * Eine unveränderliche Datenklasse, die den Speicherort und die Ausdehnung eines
 * in einer Audiodatei gefundenen Tags beschreibt. Der Offset gibt die Startposition
 * des Tags in der Datei an, die Größe die Länge des Tag-Blocks in Byte.
 */
public class TagInfo {

    /** Das erkannte Tag-Format. */
    private final TagFormat format;

    /** Die Startposition des Tags in der Datei (Offset in Byte). */
    private final long offset;

    /** Die Größe des Tag-Blocks in Byte. */
    private final long size;

    /**
     * Erstellt eine neue TagInfo-Instanz.
     *
     * @param format das erkannte Tag-Format, darf nicht {@code null} sein
     * @param offset die Startposition des Tags in der Datei in Byte
     * @param size   die Größe des Tag-Blocks in Byte
     */
    public TagInfo(TagFormat format, long offset, long size) {
        this.format = format;
        this.offset = offset;
        this.size = size;
    }

    /**
     * Gibt das erkannte Tag-Format zurück.
     *
     * @return das Tag-Format
     */
    public TagFormat getFormat() {
        return format;
    }

    /**
     * Gibt die Startposition des Tags in der Datei zurück.
     *
     * @return den Offset in Byte
     */
    public long getOffset() {
        return offset;
    }

    /**
     * Gibt die Größe des Tag-Blocks zurück.
     *
     * @return die Größe in Byte
     */
    public long getSize() {
        return size;
    }

    /**
     * Vergleicht dieses Objekt auf Gleichheit mit einem anderen.
     * <p>
     * Zwei {@code TagInfo}-Instanzen sind gleich, wenn Format, Offset und Größe übereinstimmen.
     *
     * @param o das Vergleichsobjekt
     * @return {@code true}, wenn die Objekte gleich sind, sonst {@code false}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TagInfo tagInfo = (TagInfo) o;
        return offset == tagInfo.offset && size == tagInfo.size && format == tagInfo.format;
    }

    /**
     * Berechnet den Hash-Code basierend auf Format, Offset und Größe.
     *
     * @return den Hash-Code
     */
    @Override
    public int hashCode() {
        return Objects.hash(format, offset, size);
    }

    /**
     * Gibt eine textuelle Darstellung dieser Tag-Info zurück.
     *
     * @return eine Zeichenkette mit Formatname, Offset und Größe
     */
    @Override
    public String toString() {
        return "TagInfo{format=" + format.getFormatName() +
                ", offset=" + offset +
                ", size=" + size + '}';
    }
}