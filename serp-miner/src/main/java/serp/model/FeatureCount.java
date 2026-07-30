package serp.model;

import java.util.List;

/** A distinctive feature and how many distinct source pages ("systems") mention it. */
public record FeatureCount(String feature, int systemCount, List<String> sourceLinks) implements Ranked {
    @Override
    public String label() {
        return feature;
    }

    @Override
    public int count() {
        return systemCount;
    }
}
