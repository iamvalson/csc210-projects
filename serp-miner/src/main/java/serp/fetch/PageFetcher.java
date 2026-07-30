package serp.fetch;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import serp.model.PageContent;
import serp.model.SearchResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

/**
 * Fetches every SERP result's page concurrently. A single slow or dead site
 * cannot stall the batch: each fetch has its own timeout and failures are
 * logged and skipped rather than propagated.
 */
public class PageFetcher {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private final HttpClient httpClient;
    private final int threadPoolSize;

    public PageFetcher(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public List<PageContent> fetchAll(List<SearchResult> results) {
        return fetchAll(results, completed -> {
        });
    }

    /**
     * @param onPageFetched called with the running completed-count (1..results.size())
     *                       after each page finishes, success or failure, so a caller
     *                       (e.g. the GUI) can show live "x of y fetched" progress.
     */
    public List<PageContent> fetchAll(List<SearchResult> results, IntConsumer onPageFetched) {
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Math.min(threadPoolSize, results.size())));
        try {
            List<CompletableFuture<PageContent>> futures = new ArrayList<>();
            for (SearchResult result : results) {
                futures.add(CompletableFuture.supplyAsync(() -> fetchOne(result), pool));
            }

            List<PageContent> pages = new ArrayList<>();
            int completed = 0;
            for (CompletableFuture<PageContent> future : futures) {
                PageContent page = future.join();
                completed++;
                onPageFetched.accept(completed);
                if (page != null) {
                    pages.add(page);
                }
            }
            return pages;
        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(REQUEST_TIMEOUT.toSeconds() + 5, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private PageContent fetchOne(SearchResult result) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(result.link()))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "Mozilla/5.0 (compatible; SerpMinerBot/1.0)")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.err.println("[fetch] skipped " + result.link() + " (HTTP " + response.statusCode() + ")");
                return null;
            }

            Document doc = Jsoup.parse(response.body(), result.link());
            String bodyText = doc.body() != null ? doc.body().text() : "";

            List<String> headings = new ArrayList<>();
            for (var heading : doc.select("h1, h2, h3, h4")) {
                String text = heading.text().trim();
                if (!text.isEmpty()) {
                    headings.add(text);
                }
            }

            return new PageContent(result, bodyText, headings);
        } catch (Exception e) {
            System.err.println("[fetch] skipped " + result.link() + " (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
            return null;
        }
    }
}
