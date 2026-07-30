package serp.model;

/** A single organic entry from a Serper SERP response. */
public record SearchResult(int position, String title, String link, String snippet) {
}
