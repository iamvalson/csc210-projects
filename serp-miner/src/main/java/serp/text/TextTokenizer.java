package serp.text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared, non-AI text-statistics helpers: significant-word detection and
 * frequency counting. Used by both {@link serp.summarize.ExtractiveSummarizer}
 * (sentence scoring) and {@link serp.extract.FeatureExtractor} (n-gram mining)
 * so "significant word" means the same thing in both places.
 */
public final class TextTokenizer {

    public static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z]{3,}");

    public static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "are", "but", "not", "you", "all", "any", "can",
            "had", "her", "was", "one", "our", "out", "day", "get", "has", "him",
            "his", "how", "man", "new", "now", "old", "see", "two", "way", "who",
            "boy", "did", "its", "let", "put", "say", "she", "too", "use", "with",
            "that", "this", "have", "from", "they", "will", "would", "there",
            "their", "what", "about", "which", "when", "make", "like", "time",
            "just", "into", "over", "such", "than", "then", "them", "these",
            "some", "could", "other", "more", "also", "been", "were", "your",
            "each", "most", "used", "using", "based", "where", "while", "after",
            "being", "both", "does", "doing", "having", "here", "only", "same",
            "should", "through", "very", "because", "between", "during", "further",
            "itself", "once", "under", "until", "within", "without");

    private TextTokenizer() {
    }

    /** True if the word (any case) is 3+ letters and not a stopword. */
    public static boolean isSignificant(String word) {
        return word.length() >= 3 && !STOPWORDS.contains(word.toLowerCase(Locale.ROOT));
    }

    /** All 3+ letter words in the text, in original order and casing. */
    public static List<String> extractWordsInOrder(String text) {
        List<String> words = new ArrayList<>();
        Matcher matcher = WORD_PATTERN.matcher(text);
        while (matcher.find()) {
            words.add(matcher.group());
        }
        return words;
    }

    /** Lowercased frequency histogram of significant (non-stopword) words across all texts. */
    public static Map<String, Integer> buildWordFrequency(List<String> texts) {
        Map<String, Integer> frequency = new HashMap<>();
        for (String text : texts) {
            Matcher matcher = WORD_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
            while (matcher.find()) {
                String word = matcher.group();
                if (!STOPWORDS.contains(word)) {
                    frequency.merge(word, 1, Integer::sum);
                }
            }
        }
        return frequency;
    }
}
