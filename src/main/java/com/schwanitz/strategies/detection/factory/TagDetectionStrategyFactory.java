package com.schwanitz.strategies.detection.factory;

import com.schwanitz.strategies.detection.*;
import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Fabrik zur Erstellung von Tag-Erkennungsstrategien anhand angeforderter Tag-Formate.
 * <p>
 * Diese Klasse implementiert das Factory-Pattern und ordnet jedes bekannte {@link TagFormat}
 * einer entsprechenden {@link TagDetectionStrategy}-Instanz zu. Da die Strategien zustandslos
 * und unveränderlich sind, werden sie als Singletons vorgehalten und thread-sicher verwendet.
 * <p>
 * Mehrere Tag-Formate können derselben Strategie-Instanz zugeordnet sein (z. B. werden
 * ID3V1 und ID3V1_1 beide von {@link ID3V1DetectionStrategy} erkannt). Bei der Abfrage
 * erfolgt eine automatische Deduplizierung, sodass jede Strategie nur einmal zurückgegeben wird.
 * <p>
 * Die Factory ist instanzbasiert und kann für Unit-Tests mit einem benutzerdefinierten
 * Mapping instanziiert werden.
 *
 * @see TagDetectionStrategy
 * @see TagFormat
 */
public class TagDetectionStrategyFactory {

    private final Map<TagFormat, TagDetectionStrategy> formatToStrategy;

    /**
     * Erzeugt eine neue Factory mit allen Standard-Erkennungsstrategien.
     */
    public TagDetectionStrategyFactory() {
        ID3V1DetectionStrategy id3v1Strategy = new ID3V1DetectionStrategy();
        ID3V2DetectionStrategy id3v2Strategy = new ID3V2DetectionStrategy();
        APEDetectionStrategy apeStrategy = new APEDetectionStrategy();
        VorbisCommentDetectionStrategy vorbisStrategy = new VorbisCommentDetectionStrategy();
        MP4DetectionStrategy mp4Strategy = new MP4DetectionStrategy();
        WAVDetectionStrategy wavStrategy = new WAVDetectionStrategy();
        ASFDetectionStrategy asfStrategy = new ASFDetectionStrategy();
        FLACApplicationDetectionStrategy flacAppStrategy = new FLACApplicationDetectionStrategy();
        MatroskaDetectionStrategy matroskaStrategy = new MatroskaDetectionStrategy();
        DSDDetectionStrategy dsdStrategy = new DSDDetectionStrategy();
        TTADetectionStrategy ttaStrategy = new TTADetectionStrategy();
        WavPackDetectionStrategy wavPackStrategy = new WavPackDetectionStrategy();
        AIFFDetectionStrategy aiffStrategy = new AIFFDetectionStrategy();
        Lyrics3DetectionStrategy lyrics3Strategy = new Lyrics3DetectionStrategy();

        this.formatToStrategy = Map.ofEntries(
                // ID3 Formate
                Map.entry(TagFormat.ID3V1, id3v1Strategy),
                Map.entry(TagFormat.ID3V1_1, id3v1Strategy),
                Map.entry(TagFormat.ID3V2_2, id3v2Strategy),
                Map.entry(TagFormat.ID3V2_3, id3v2Strategy),
                Map.entry(TagFormat.ID3V2_4, id3v2Strategy),

                // APE Formate
                Map.entry(TagFormat.APEV1, apeStrategy),
                Map.entry(TagFormat.APEV2, apeStrategy),

                // Andere Formate
                Map.entry(TagFormat.VORBIS_COMMENT, vorbisStrategy),
                Map.entry(TagFormat.MP4, mp4Strategy),

                // WAV Formate
                Map.entry(TagFormat.RIFF_INFO, wavStrategy),
                Map.entry(TagFormat.BWF_V0, wavStrategy),
                Map.entry(TagFormat.BWF_V1, wavStrategy),
                Map.entry(TagFormat.BWF_V2, wavStrategy),

                // ASF Formate
                Map.entry(TagFormat.ASF_CONTENT_DESC, asfStrategy),
                Map.entry(TagFormat.ASF_EXT_CONTENT_DESC, asfStrategy),

                // Weitere Formate
                Map.entry(TagFormat.FLAC_APPLICATION, flacAppStrategy),
                Map.entry(TagFormat.MATROSKA_TAGS, matroskaStrategy),
                Map.entry(TagFormat.WEBM_TAGS, matroskaStrategy),
                Map.entry(TagFormat.DSF_METADATA, dsdStrategy),
                Map.entry(TagFormat.DFF_METADATA, dsdStrategy),
                Map.entry(TagFormat.TTA_METADATA, ttaStrategy),
                Map.entry(TagFormat.WAVPACK_NATIVE, wavPackStrategy),
                Map.entry(TagFormat.AIFF_METADATA, aiffStrategy),
                Map.entry(TagFormat.LYRICS3V1, lyrics3Strategy),
                Map.entry(TagFormat.LYRICS3V2, lyrics3Strategy)
        );
    }

    /**
     * Erzeugt eine neue Factory mit einem benutzerdefinierten Strategie-Mapping.
     * <p>Nützlich für Unit-Tests mit gemockten Strategien.</p>
     *
     * @param formatToStrategy das Format-zu-Strategie-Mapping
     */
    public TagDetectionStrategyFactory(Map<TagFormat, TagDetectionStrategy> formatToStrategy) {
        this.formatToStrategy = Collections.unmodifiableMap(formatToStrategy);
    }

    /**
     * Konvertiert eine Liste von TagFormats zu einer Liste von eindeutigen Strategien.
     * <p>
     * Die zurückgegebenen Strategien werden automatisch dedupliziert, da mehrere
     * Tag-Formate auf dieselbe Strategie-Instanz abgebildet werden können.
     *
     * @param formats Liste der gewünschten {@link TagFormat}-Werte
     * @return Liste von eindeutigen {@link TagDetectionStrategy}-Instanzen; leer,
     *         wenn keine passenden Strategien gefunden wurden
     */
    public List<TagDetectionStrategy> getStrategiesForFormats(List<TagFormat> formats) {
        return formats.stream()
                .map(formatToStrategy::get)
                .filter(Objects::nonNull)
                .distinct()  // Automatische Deduplizierung
                .collect(Collectors.toList());
    }
}
