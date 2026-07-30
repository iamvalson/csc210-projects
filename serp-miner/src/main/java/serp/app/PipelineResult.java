package serp.app;

import org.jfree.chart.JFreeChart;
import serp.model.PageContent;
import serp.model.PageSummary;
import serp.model.Ranked;
import serp.model.SearchResult;

import java.util.List;

public record PipelineResult(
        String query,
        List<SearchResult> serpResults,
        List<PageContent> pages,
        List<PageSummary> pageSummaries,
        List<? extends Ranked> ranked,
        String resultLabel,
        String summary,
        JFreeChart chart) {
}
