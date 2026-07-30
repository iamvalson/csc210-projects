package serp;

import org.junit.jupiter.api.Test;
import serp.extract.FeatureExtractor;
import serp.extract.HeadingExtractor;
import serp.model.FeatureCount;
import serp.model.HeadingEntry;
import serp.model.PageContent;
import serp.model.SearchResult;
import serp.summarize.ExtractiveSummarizer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineComponentsTest {

    private PageContent page(String link, String bodyText, List<String> headings) {
        return new PageContent(new SearchResult(1, "title", link, "snippet"), bodyText, headings);
    }

    @Test
    void featureExtractorMinesRecurringPhrasesAcrossDistinctPages() {
        List<PageContent> pages = List.of(
                page("https://a.example", "The app supports location tracking and instant crime alerts for nearby users.", List.of()),
                page("https://b.example", "This platform provides location tracking as well as community reporting tools.", List.of()),
                page("https://c.example", "Users receive instant crime alerts plus location tracking updates quickly.", List.of()));

        List<FeatureCount> counts = new FeatureExtractor().extract(pages)
                .stream().map(r -> (FeatureCount) r).toList();

        assertEquals(2, counts.size(), "phrases seen on only one page should be filtered out");
        assertEquals("location tracking", counts.get(0).feature().toLowerCase());
        assertEquals(3, counts.get(0).systemCount(), "location tracking appears on all 3 pages");
        assertEquals("instant crime alerts", counts.get(1).feature().toLowerCase());
        assertEquals(2, counts.get(1).systemCount(), "the trigram should subsume its constituent bigrams, not list them separately");
    }

    @Test
    void featureExtractorRelaxesThresholdWhenOnlyOnePageWasFetched() {
        List<PageContent> pages = List.of(
                page("https://a.example", "Officers track incidents using mobile devices constantly.", List.of()));

        List<FeatureCount> counts = new FeatureExtractor().extract(pages)
                .stream().map(r -> (FeatureCount) r).toList();

        assertFalse(counts.isEmpty(), "with a single fetched page, the minimum system count should relax to 1");
    }

    @Test
    void headingExtractorDeduplicatesCaseInsensitively() {
        List<PageContent> pages = List.of(
                page("https://a.example", "", List.of("Introduction", "Related Work")),
                page("https://b.example", "", List.of("introduction", "Methodology")));

        List<HeadingEntry> headings = new HeadingExtractor().extract(pages)
                .stream().map(r -> (HeadingEntry) r).toList();

        assertEquals(3, headings.size());
        assertEquals("Introduction", headings.get(0).heading());
        assertEquals(2, headings.get(0).occurrenceCount());
    }

    @Test
    void summarizerPrefersSentencesWithFrequentSignificantWords() {
        String text = "Deep learning models require large datasets for training. "
                + "Deep learning models are widely used in image recognition tasks today. "
                + "The weather was pleasant during the conference this year.";
        List<PageContent> pages = List.of(page("https://a.example", text, List.of()));

        String summary = new ExtractiveSummarizer().summarize(pages, 1);

        assertFalse(summary.isBlank());
        assertTrue(summary.toLowerCase().contains("deep learning"));
    }
}
