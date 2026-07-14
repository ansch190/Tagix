package com.schwanitz.tagging;

/**
 * Aufzählung der verfügbaren Schreibmodi für Tag-Schreibvorgänge.
 */
public enum WriteMode {

    /** Nur neue Tags schreiben, keine bestehenden einbeziehen. */
    CREATE_NEW,

    /** Bestehende Tags aktualisieren oder neue hinzufügen. */
    UPDATE_EXISTING,

    /** Alle Tags des angegebenen Formats vollständig ersetzen. */
    REPLACE_ALL,

    /** Tags des angegebenen Formats entfernen. */
    REMOVE
}
