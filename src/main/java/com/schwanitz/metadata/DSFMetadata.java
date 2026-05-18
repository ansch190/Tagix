package com.schwanitz.metadata;

import com.schwanitz.tagging.TagFormat;

/**
 * Spezialisierte {@link GenericMetadata}-Implementierung für DSF-Dateien.
 * <p>
 * Erweitert die generische Metadaten-Klasse um DSF-spezifische Felder für
 * Offset und Größe des eingebetteten ID3-Datenblocks.
 * </p>
 *
 * @see GenericMetadata
 */
public class DSFMetadata extends GenericMetadata {

    private long id3DataOffset = -1;
    private long id3DataSize = 0;

    /**
     * Erzeugt eine neue DSF-Metadaten-Instanz.
     */
    public DSFMetadata() {
        super(TagFormat.DSF_METADATA);
    }

    /**
     * Liefert den Offset des eingebetteten ID3-Datenblocks in der Datei.
     *
     * @return der Offset oder -1, wenn kein ID3-Block gefunden wurde
     */
    public long getId3DataOffset() {
        return id3DataOffset;
    }

    /**
     * Liefert die Größe des eingebetteten ID3-Datenblocks in Bytes.
     *
     * @return die Größe in Bytes
     */
    public long getId3DataSize() {
        return id3DataSize;
    }

    /**
     * Setzt den Offset des eingebetteten ID3-Datenblocks.
     *
     * @param offset der neue Offset
     */
    public void setId3DataOffset(long offset) {
        this.id3DataOffset = offset;
    }

    /**
     * Setzt die Größe des eingebetteten ID3-Datenblocks.
     *
     * @param size die neue Größe in Bytes
     */
    public void setId3DataSize(long size) {
        this.id3DataSize = size;
    }
}
