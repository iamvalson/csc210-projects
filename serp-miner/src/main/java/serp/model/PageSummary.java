package serp.model;

/** A short, per-page extractive summary paired with the title/link it belongs to, for display. */
public record PageSummary(String title, String link, String summary) {
}
