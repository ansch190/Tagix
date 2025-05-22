package com.schwanitz;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.ID3Metadata;
import com.schwanitz.others.MetadataField;
import com.schwanitz.others.MetadataManager;
import com.schwanitz.others.TextFieldHandler;
import com.schwanitz.tagging.TagFormat;

import java.io.IOException;

public class Main {

    public static void main(String[] args) {
        try {
            // MetadataManager initialisieren
            MetadataManager manager = new MetadataManager();

            // MP3-Metadaten erstellen
            ID3Metadata mp3Metadata = new ID3Metadata(TagFormat.ID3V2_3);

            // Handler für Text- und Zahlenfelder
            TextFieldHandler titleHandler = new TextFieldHandler("TIT2");
            TextFieldHandler artistHandler = new TextFieldHandler("TPE1");

            // Felder hinzufügen
            mp3Metadata.addField(new MetadataField<>("TIT2", "Beispiel Titel", titleHandler));
            mp3Metadata.addField(new MetadataField<>("TPE1", "Beispiel Künstler", artistHandler));

            // Metadaten zum Manager hinzufügen
            manager.addMetadata(mp3Metadata);

            // Metadaten ausgeben
            Metadata metadata = manager.getMetadata("MP3");
            if (metadata != null) {
                for (MetadataField<?> field : metadata.getFields()) {
                    System.out.println("Feld: " + field.getKey() + ", Wert: " + field.getValue());
                }
            }

            // Beispiel: Wert ändern
            for (MetadataField<?> field : mp3Metadata.getFields()) {
                if (field.getKey().equals("TIT2")) {
                    ((MetadataField<String>) field).setValue("Neuer Titel");
                }
            }

            // Geänderte Metadaten ausgeben
            System.out.println("\nNach Änderung:");
            for (MetadataField<?> field : mp3Metadata.getFields()) {
                System.out.println("Feld: " + field.getKey() + ", Wert: " + field.getValue());
            }

            // In Datei schreiben (Platzhalter)
            manager.writeToFile("example.mp3");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}