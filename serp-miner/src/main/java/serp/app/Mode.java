package serp.app;

import java.util.Locale;

/** Which extractor the pipeline runs: the two numbered parts of the assignment. */
public enum Mode {
    FEATURES,
    HEADINGS;

    public static Mode fromFlag(String flag) {
        return switch (flag.toLowerCase(Locale.ROOT)) {
            case "features" -> FEATURES;
            case "headings" -> HEADINGS;
            default -> throw new IllegalArgumentException("Unknown mode: " + flag + " (expected features|headings)");
        };
    }
}
