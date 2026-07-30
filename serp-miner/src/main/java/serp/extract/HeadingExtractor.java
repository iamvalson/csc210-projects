package serp.extract;

import serp.model.HeadingEntry;
import serp.model.PageContent;
import serp.model.Ranked;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collects distinct sub-headings (h1-h4) across all fetched paper pages,
 * deduplicated by normalized text, ranked by how many distinct pages use them.
 */
public class HeadingExtractor implements Extractor {

    @Override
    public List<? extends Ranked> extract(List<PageContent> pages) {
        Map<String, String> displayTextByKey = new LinkedHashMap<>();
        Map<String, Set<String>> sourcesByKey = new LinkedHashMap<>();

        for (PageContent page : pages) {
            for (String heading : page.headings()) {
                String normalized = normalize(heading);
                if (normalized.length() < 3) {
                    continue;
                }
                displayTextByKey.putIfAbsent(normalized, heading.trim());
                sourcesByKey.computeIfAbsent(normalized, k -> new LinkedHashSet<>()).add(page.source().link());
            }
        }

        List<HeadingEntry> entries = new ArrayList<>();
        for (Map.Entry<String, String> entry : displayTextByKey.entrySet()) {
            Set<String> sources = sourcesByKey.get(entry.getKey());
            entries.add(new HeadingEntry(entry.getValue(), sources.size(), List.copyOf(sources)));
        }

        entries.sort(Comparator.comparingInt(HeadingEntry::occurrenceCount).reversed()
                .thenComparing(HeadingEntry::heading, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private String normalize(String text) {
        return text.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    @Override
    public String resultLabel() {
        return "Distinct sub-headings";
    }
}
