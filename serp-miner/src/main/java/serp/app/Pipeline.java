package serp.app;

import org.jfree.chart.JFreeChart;
import serp.api.SerperClient;
import serp.extract.Extractor;
import serp.extract.FeatureExtractor;
import serp.extract.HeadingExtractor;
import serp.fetch.PageFetcher;
import serp.model.PageContent;
import serp.model.PageSummary;
import serp.model.Ranked;
import serp.model.SearchResult;
import serp.summarize.ExtractiveSummarizer;
import serp.viz.ChartRenderer;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Orchestrates the full query-to-visualization flow shared by both
 * assignment tasks: SERP lookup, concurrent page fetch, extraction,
 * summarization, and chart rendering. The {@code onStatus} callback lets a
 * caller (CLI or GUI) show live progress without this class knowing anything
 * about how that progress is displayed.
 */
public class Pipeline {

    private static final int SUMMARY_SENTENCES = 8;
    private static final int PAGE_SUMMARY_SENTENCES = 2;
    private static final int CHART_MAX_ITEMS = 20;

    private final SerperClient serperClient;
    private final PageFetcher pageFetcher;
    private final ExtractiveSummarizer summarizer = new ExtractiveSummarizer();
    private final ChartRenderer chartRenderer = new ChartRenderer();

    public Pipeline(String apiKey, int threadPoolSize) {
        this.serperClient = new SerperClient(apiKey);
        this.pageFetcher = new PageFetcher(threadPoolSize);
    }

    public PipelineResult run(String query, Mode mode, int numResults) throws IOException, InterruptedException {
        return run(query, mode, numResults, message -> {
        });
    }

    public PipelineResult run(String query, Mode mode, int numResults, Consumer<String> onStatus)
            throws IOException, InterruptedException {
        onStatus.accept("Searching Serper for \"" + query + "\"...");
        List<SearchResult> serpResults = serperClient.search(query, numResults);

        onStatus.accept("Fetching " + serpResults.size() + " pages (0/" + serpResults.size() + ")...");
        List<PageContent> pages = pageFetcher.fetchAll(serpResults, completed ->
                onStatus.accept("Fetching pages (" + completed + "/" + serpResults.size() + ")..."));

        onStatus.accept("Extracting " + (mode == Mode.FEATURES ? "features" : "sub-headings") + "...");
        Extractor extractor = mode == Mode.FEATURES ? new FeatureExtractor() : new HeadingExtractor();
        List<? extends Ranked> ranked = extractor.extract(pages);

        onStatus.accept("Summarizing " + pages.size() + " pages...");
        String summary = summarizer.summarize(pages, SUMMARY_SENTENCES);
        List<PageSummary> pageSummaries = pages.stream().map(this::summarizePage).toList();

        onStatus.accept("Rendering chart...");
        JFreeChart chart = chartRenderer.createBarChart(
                extractor.resultLabel() + " — \"" + query + "\"", ranked, CHART_MAX_ITEMS);

        onStatus.accept("Done. Fetched " + pages.size() + " of " + serpResults.size() + " pages.");
        return new PipelineResult(query, serpResults, pages, pageSummaries, ranked, extractor.resultLabel(), summary, chart);
    }

    private PageSummary summarizePage(PageContent page) {
        String shortSummary = summarizer.summarize(List.of(page), PAGE_SUMMARY_SENTENCES);
        if (shortSummary.isBlank()) {
            shortSummary = page.source().snippet();
        }
        return new PageSummary(page.source().title(), page.source().link(), shortSummary);
    }

    public ChartRenderer chartRenderer() {
        return chartRenderer;
    }
}
