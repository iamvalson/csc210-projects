package serp.model;

import java.util.List;

/**
 * The fetched and parsed content of one SERP result page.
 * {@code headings} holds the page's h1-h4 text, in document order.
 */
public record PageContent(SearchResult source, String bodyText, List<String> headings) {
}
