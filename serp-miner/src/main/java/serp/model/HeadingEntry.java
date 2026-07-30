package serp.model;

import java.util.List;

/** A distinct sub-heading text and how many source pages contain it. */
public record HeadingEntry(String heading, int occurrenceCount, List<String> sourceLinks) implements Ranked {
    @Override
    public String label() {
        return heading;
    }

    @Override
    public int count() {
        return occurrenceCount;
    }
}
