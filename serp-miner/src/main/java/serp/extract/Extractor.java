package serp.extract;

import serp.model.PageContent;
import serp.model.Ranked;

import java.util.List;

/** Turns fetched page content into a ranked list plottable on the bar chart. */
public interface Extractor {

    List<? extends Ranked> extract(List<PageContent> pages);

    /** Label used for chart titles / CLI headers, e.g. "Distinctive features". */
    String resultLabel();
}
