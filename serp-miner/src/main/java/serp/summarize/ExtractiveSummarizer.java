package serp.summarize;

import serp.model.PageContent;
import serp.text.TextTokenizer;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * Non-AI extractive summarizer: a simplified Luhn-style algorithm. Sentences
 * are scored by the average frequency of their significant (non-stopword)
 * words across the whole fetched corpus; the highest-scoring sentences are
 * picked and re-ordered to their original position to read as one summary.
 * No model calls, no external inference - just word-frequency statistics.
 */
public class ExtractiveSummarizer {

    private static final int MIN_WORDS_PER_SENTENCE = 6;

    public String summarize(List<PageContent> pages, int maxSentences) {
        List<String> sentences = new ArrayList<>();
        for (PageContent page : pages) {
            sentences.addAll(splitSentences(page.bodyText()));
        }
        if (sentences.isEmpty()) {
            return "";
        }

        Map<String, Integer> frequency = TextTokenizer.buildWordFrequency(sentences);

        List<ScoredSentence> scored = new ArrayList<>();
        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            scored.add(new ScoredSentence(i, sentence, scoreSentence(sentence, frequency)));
        }

        List<ScoredSentence> top = scored.stream()
                .sorted(Comparator.comparingDouble(ScoredSentence::score).reversed())
                .limit(maxSentences)
                .sorted(Comparator.comparingInt(ScoredSentence::order))
                .toList();

        return top.stream().map(ScoredSentence::text).collect(Collectors.joining(" "));
    }

    private List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
        iterator.setText(text);

        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String sentence = text.substring(start, end).trim();
            if (sentence.split("\\s+").length >= MIN_WORDS_PER_SENTENCE) {
                sentences.add(sentence);
            }
        }
        return sentences;
    }

    private double scoreSentence(String sentence, Map<String, Integer> frequency) {
        Matcher matcher = TextTokenizer.WORD_PATTERN.matcher(sentence.toLowerCase(Locale.ROOT));
        int total = 0;
        int significantWords = 0;
        while (matcher.find()) {
            Integer count = frequency.get(matcher.group());
            if (count != null) {
                total += count;
                significantWords++;
            }
        }
        return significantWords == 0 ? 0 : (double) total / significantWords;
    }

    private record ScoredSentence(int order, String text, double score) {
    }
}
