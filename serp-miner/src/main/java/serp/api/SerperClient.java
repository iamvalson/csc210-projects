package serp.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import serp.model.SearchResult;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Thin wrapper around the Serper.dev "/search" endpoint (Google SERP as JSON). */
public class SerperClient {

    private static final String SEARCH_URL = "https://google.serper.dev/search";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String apiKey;
    private final HttpClient httpClient;

    public SerperClient(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Runs a SERP query and returns the organic results, ranked as Serper returned them.
     */
    public List<SearchResult> search(String query, int numResults) throws IOException, InterruptedException {
        String body = JSON.createObjectNode()
                .put("q", query)
                .put("num", numResults)
                .toString();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SEARCH_URL))
                .header("X-API-KEY", apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Serper API request failed: HTTP " + response.statusCode() + " - " + response.body());
        }

        return parseOrganicResults(response.body());
    }

    private List<SearchResult> parseOrganicResults(String responseBody) throws IOException {
        JsonNode root = JSON.readTree(responseBody);
        JsonNode organic = root.path("organic");

        List<SearchResult> results = new ArrayList<>();
        for (JsonNode entry : organic) {
            int position = entry.path("position").asInt(results.size() + 1);
            String title = entry.path("title").asText("");
            String link = entry.path("link").asText("");
            String snippet = entry.path("snippet").asText("");
            if (!link.isBlank()) {
                results.add(new SearchResult(position, title, link, snippet));
            }
        }
        return results;
    }
}
