package serp.extract;

import serp.model.FeatureCount;
import serp.model.PageContent;
import serp.model.Ranked;
import serp.text.TextTokenizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Dynamically mines "distinctive features" from whatever was fetched, with no
 * topic-specific keyword list: candidate features are 2- and 3-word phrases
 * built from consecutive significant (non-stopword) words - a crude but
 * deterministic, non-AI stand-in for noun-phrase chunking - then ranked by how
 * many distinct source pages ("systems") contain each phrase.
 *
 * Works for any query/topic, since it derives phrases purely from the fetched
 * page text rather than a predefined lexicon.
 */
public class FeatureExtractor implements Extractor {

    private static final int DEFAULT_MIN_SYSTEM_COUNT = 2;
    private static final int DEFAULT_MAX_RESULTS = 30;

    private final int minSystemCount;
    private final int maxResults;

    public FeatureExtractor() {
        this(DEFAULT_MIN_SYSTEM_COUNT, DEFAULT_MAX_RESULTS);
    }

    public FeatureExtractor(int minSystemCount, int maxResults) {
        this.minSystemCount = minSystemCount;
        this.maxResults = maxResults;
    }

    @Override
    public List<? extends Ranked> extract(List<PageContent> pages) {
        Map<String, String> displayByPhrase = new LinkedHashMap<>();
        Map<String, Set<String>> sourcesByPhrase = new LinkedHashMap<>();

        for (PageContent page : pages) {
            List<String> orderedWords = TextTokenizer.extractWordsInOrder(page.bodyText());
            Set<String> phrasesOnThisPage = new LinkedHashSet<>();
            phrasesOnThisPage.addAll(buildNGrams(orderedWords, 3, displayByPhrase));
            phrasesOnThisPage.addAll(buildNGrams(orderedWords, 2, displayByPhrase));

            for (String phrase : phrasesOnThisPage) {
                sourcesByPhrase.computeIfAbsent(phrase, k -> new LinkedHashSet<>()).add(page.source().link());
            }
        }

        removeBigramsSubsumedByTrigrams(sourcesByPhrase, displayByPhrase);

        int effectiveMin = pages.isEmpty() ? 1 : Math.max(1, Math.min(minSystemCount, pages.size()));

        List<FeatureCount> counts = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : sourcesByPhrase.entrySet()) {
            Set<String> sources = entry.getValue();
            if (sources.size() >= effectiveMin) {
                counts.add(new FeatureCount(displayByPhrase.get(entry.getKey()), sources.size(), List.copyOf(sources)));
            }
        }

        counts.sort(Comparator.comparingInt(FeatureCount::systemCount).reversed()
                .thenComparing((FeatureCount f) -> f.feature().split(" ").length, Comparator.reverseOrder())
                .thenComparing(FeatureCount::feature, String.CASE_INSENSITIVE_ORDER));

        return counts.size() > maxResults ? counts.subList(0, maxResults) : counts;
    }

    /**
     * Builds n-grams from consecutive words that are ALL significant (no stopword
     * bridging them), preserving original casing for display and using a
     * lowercase, whitespace-normalized key for cross-page deduplication.
     */
    private List<String> buildNGrams(List<String> orderedWords, int n, Map<String, String> displayByPhrase) {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i + n <= orderedWords.size(); i++) {
            List<String> window = orderedWords.subList(i, i + n);
            if (window.stream().allMatch(TextTokenizer::isSignificant)) {
                String display = String.join(" ", window);
                String key = display.toLowerCase(Locale.ROOT);
                displayByPhrase.putIfAbsent(key, display);
                keys.add(key);
            }
        }
        return keys;
    }

    /**
     * If a 2-word phrase's source set is identical to a 3-word phrase that
     * contains it, the bigram adds no information beyond the trigram - drop it
     * so the ranked list favors the more specific phrase.
     */
    private void removeBigramsSubsumedByTrigrams(Map<String, Set<String>> sourcesByPhrase, Map<String, String> displayByPhrase) {
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : sourcesByPhrase.entrySet()) {
            String[] words = entry.getKey().split(" ");
            if (words.length != 3) {
                continue;
            }
            Set<String> trigramSources = entry.getValue();
            for (String sub : List.of(words[0] + " " + words[1], words[1] + " " + words[2])) {
                Set<String> subSources = sourcesByPhrase.get(sub);
                if (subSources != null && subSources.equals(trigramSources)) {
                    toRemove.add(sub);
                }
            }
        }
        toRemove.forEach(sourcesByPhrase::remove);
        toRemove.forEach(displayByPhrase::remove);
    }

    @Override
    public String resultLabel() {
        return "Distinctive features (by number of systems)";
    }
}
