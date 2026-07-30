package serp.model;

import java.util.List;

/** Common shape for anything that can be plotted on the ranked bar chart / written to CSV. */
public interface Ranked {
    String label();

    int count();

    List<String> sourceLinks();
}
