# SERP Miner — Current Project State

_Last updated: 2026-07-30_

## 1. What this is

A multithreaded Java 17 program for Assignment 1 (SERP mining/summarization).
Given a query, it pulls a Search Engine Results Page via the **Serper API**,
fetches every result page **concurrently**, and mines the fetched text into:

1. A ranked list (bar chart) via one of two pluggable extractors, and
2. A single combined summary — built **without any AI/ML model**, using a
   classic word-frequency (Luhn-style) extractive algorithm.

The JavaFX GUI additionally shows each SERP result as a readable card: title,
clickable link, and a short 1-2 sentence per-page summary underneath it
(same non-AI extractive approach, run per-page instead of across everything).
While a query runs, a spinner and a live-updating status label show what
stage the pipeline is in ("Searching Serper...", "Fetching pages (3/10)...",
"Extracting features...", "Summarizing...", etc.), and a header above the
results shows the query that produced them, independent of the input field.

## 2. Assignment mapping

| Assignment ask | How it's implemented |
|---|---|
| 1. Distinctive features (≥10), categorized by number of systems having the feature, visualized | `FeatureExtractor` → `--mode=features`. Dynamically mines 2- and 3-word phrases from the fetched text (no fixed topic lexicon — works for any query), ranks by number of distinct source pages containing each phrase, plotted as a bar chart. |
| 2. Distinct sub-headings of journal papers, visualized | `HeadingExtractor` → `--mode=headings`. Collects `h1`-`h4` text across all fetched papers, dedups case-insensitively, ranks by how many pages use each heading, plotted as a bar chart. |
| Multithreaded | `PageFetcher` fetches all SERP result pages in parallel via `ExecutorService` + `CompletableFuture`, each with its own timeout; failed/slow pages are skipped, not fatal. Reports live "x of y fetched" progress via a callback. |
| "Summarizing relevant content instead of titles/links" | `ExtractiveSummarizer` scores every sentence across all fetched pages by average frequency of its significant (non-stopword) words, and returns the top-N sentences re-ordered to their original position — one combined summary, no AI involved. The same method also produces a short 1-2 sentence summary per individual page (`Pipeline.summarizePage`), falling back to Serper's snippet if a page has too little text to score. |
| CSV outputs | **Intentionally removed** — see §7. Superseded by an in-app, clickable SERP-results card list in the JavaFX GUI, per explicit direction. |

## 3. Architecture (as built)

```
serp-miner/
  pom.xml                     Maven project: Jsoup, Jackson, JFreeChart, JavaFX (win classifier), JUnit 5; shade plugin -> fat jar
  mvnw / mvnw.cmd              Maven wrapper (no system-wide Maven required)
  README.md                    Setup + run instructions
  PROJECT_STATUS.md            This file
  src/main/java/serp/
    api/    SerperClient.java          POST google.serper.dev/search, X-API-KEY header, parses "organic" results
    model/  SearchResult.java          record: position, title, link, snippet
            PageContent.java           record: source, bodyText, headings (fetched + parsed per page)
            PageSummary.java           record: title, link, summary — per-page display view for the GUI card list / CLI printout
            Ranked.java                interface: label(), count(), sourceLinks() — shared shape for the chart
            FeatureCount.java          record implements Ranked: feature, systemCount, sourceLinks
            HeadingEntry.java          record implements Ranked: heading, occurrenceCount, sourceLinks
    fetch/  PageFetcher.java           fixed thread pool, per-request timeout (8s), Jsoup parse -> body text + headings;
                                       fetchAll(results, IntConsumer onPageFetched) overload reports running completed-count
    text/   TextTokenizer.java         shared stopword list + significant-word/word-order helpers (used by summarizer AND feature extractor)
    extract/
            Extractor.java             interface: extract(pages) -> List<? extends Ranked>, resultLabel()
            FeatureExtractor.java      dynamic n-gram mining (2/3-word phrases from consecutive significant words),
                                       trigram-subsumes-bigram dedup, ranks by distinct-page count — no fixed lexicon
            HeadingExtractor.java      dedups h1-h4 text across pages, ranks by occurrence
    summarize/
            ExtractiveSummarizer.java  BreakIterator sentence split, TextTokenizer-based frequency scoring, top-N reordered;
                                       called both on the whole page set (combined summary) and on a single-page list (mini summary)
    viz/    ChartRenderer.java         JFreeChart horizontal bar chart -> PNG (CLI path only; GUI uses its own JavaFX chart)
    app/    Mode.java                  FEATURES | HEADINGS enum
            Pipeline.java              orchestrates: SerperClient -> PageFetcher -> Extractor -> Summarizer -> per-page
                                       summaries -> Chart; run(..., Consumer<String> onStatus) reports per-stage progress messages
            PipelineResult.java        record: query/serpResults/pages/pageSummaries/ranked/resultLabel/summary/chart
            Cli.java                   arg parsing; streams pipeline progress to stdout, prints per-page title/link/summary,
                                       ranked list, combined summary, saves chart PNG
            Main.java                  no args (or --gui) -> JavaFX MinerApp, else -> Cli
    gui/    MinerApp.java              JavaFX: query field + mode dropdown + Run button, a query-title header shown once a
                                       run starts, a ProgressIndicator + status label driven by Task.updateMessage(),
                                       a SERP-Results ListView of card cells (bold title, clickable Hyperlink via
                                       getHostServices().showDocument(), wrapped per-page summary), a Summary tab, and
                                       the embedded BarChart; javafx.concurrent.Task keeps network I/O off the FX thread
  src/test/java/serp/
            PipelineComponentsTest.java  FeatureExtractor, HeadingExtractor, ExtractiveSummarizer tests (synthetic fixtures, no network)
```

## 4. Verified so far

- `./mvnw compile` / `./mvnw package` / `./mvnw test` all succeed cleanly (JDK 17.0.19, Maven via wrapper 3.6.3).
- `target/serp-miner.jar` (shaded, ~17.9MB) runs standalone with `java -jar`.
- Missing-API-key path confirmed via the CLI: exits with a clear error, no stack trace.
- **A real end-to-end run against the live Serper API happened previously** (you ran it and opened the resulting `SERP_Results.csv` yourself, before CSV output was removed) — the underlying fetch/extract/summarize pipeline does work against real data. That was before the per-page summaries, progress callbacks, and card-list UI in this update, so none of those specific additions have been exercised against live data yet (see §5).
- Launched `java -jar target/serp-miner.jar` with no args three times across this session's changes (Swing→JavaFX, CSV→table, and this progress/card-list update): the JavaFX GUI starts and stays running (no crash) each time — confirmed via process inspection, then killed programmatically (using `timeout`, which cleaned up correctly on the last attempt without leaving an orphaned process). **Still not visually screenshotted or clicked through.**
- All 4 unit tests pass:
  - `FeatureExtractor` — mines recurring phrases across distinct pages, correctly favors the trigram over its subsumed bigrams, correctly relaxes the minimum-page threshold to 1 for a single-page result set.
  - `HeadingExtractor` — dedups headings case-insensitively and ranks by occurrence.
  - `ExtractiveSummarizer` — picks the sentence with higher-frequency significant words over a low-signal filler sentence.

## 5. Not yet verified

- **A live run has not been done since the progress-indicator / per-page-summary / card-list changes.** Run a real query in the GUI and confirm: the status label ticks through the fetch-progress messages, the query header appears, each SERP-Results card shows a sensible 1-2 sentence summary (not empty, not just repeating the title), and links open in your default browser.
- No test against actual paywalled/JS-rendered academic sites (IEEE Xplore, ACM DL, ScienceDirect, etc.) — thin HTML from these would likely also produce a thin or missing per-page summary, falling back to Serper's raw snippet.
- No unit test yet for `Pipeline.summarizePage`'s snippet-fallback branch (when a page's body text is too short for `ExtractiveSummarizer` to produce any sentence) — only exercised implicitly, not asserted directly.

## 6. Known limitations (by design, not bugs)

- `FeatureExtractor`'s n-gram builder treats words under 3 letters (e.g. "as", "of", "to", "in") as invisible rather than as stopword-but-present — they're dropped by the tokenizing regex entirely, so two content words separated only by a short function word can appear "adjacent" in the mined phrase. This is a known quirk of the simple regex-based tokenizer, not a correctness bug; a real NLP tokenizer would handle this more precisely.
- Heading extraction relies on the page actually using semantic `<h1>`-`<h4>` tags; sites that fake headings with styled `<div>`/`<span>` won't be picked up.
- No retry logic on failed page fetches — a failed page is simply excluded from that run's results and its card list.
- No caching — every run re-queries Serper and re-fetches every page.
- The shaded jar is Windows-only (see §7).
- Fetch progress is reported as futures are `.join()`ed in submission order, not true completion order — the running count is accurate, but which specific page just finished isn't necessarily the last one shown. Fine for a coarse "x of y" indicator, not meant to be exact.
- The CLI has no equivalent of the GUI's clickable cards — it prints title/link/summary as plain text, since a terminal can't render clickable links the same way. This is an accepted CLI/GUI asymmetry, not an oversight.

## 7. Known deviations from original spec

- **CSV outputs removed entirely, replaced by an in-app card list.** The assignment spec called for CSV outputs alongside visualization, and this was originally implemented (`CsvWriter`, OpenCSV, `SERP_Results.csv` + `<Mode>_Results.csv`). It has since been **fully removed at your explicit request**, in favor of a JavaFX `ListView` card layout in the GUI showing each SERP result's title, clickable link, and a short summary, so you don't have to leave the app to browse them. This is a deliberate, requested departure from the written spec — flagging it here in case the grading rubric expects literal CSV files on disk.
- **(Superseded) Third combined CSV.** Before CSVs were removed altogether, the original spec's third CSV (top distinctive words combined across both crime-reporting and deep-learning topics) had already been dropped because `FeatureExtractor` became topic-agnostic and the app runs one query/mode per session — there was no fixed topic pair left to combine. Moot now that CSV output doesn't exist at all, but recorded for the paper trail.
- **JavaFX packaging is platform-pinned.** `pom.xml` declares `javafx-base`/`javafx-graphics`/`javafx-controls` with the `win` classifier so the shaded jar contains real native JavaFX libraries (JavaFX's own POMs don't propagate a classifier transitively — each module has to be pinned explicitly). This makes `target/serp-miner.jar` Windows-only. A cross-platform build would need per-OS classified jars selected via `os-maven-plugin` profiles, or a jlink/jpackage-based distribution instead of a single shaded jar — out of scope here since the dev/grading environment is Windows.
- **JFreeChart kept for the CLI PNG path only.** The GUI's interactive chart is pure JavaFX (`BarChart`); JFreeChart remains solely in `ChartRenderer` for `--out=chart.png` because it's the simplest way to rasterize a chart to PNG without spinning up the JavaFX toolkit for a headless CLI run. This is an intentional two-library tradeoff, not leftover dead code.

## 8. How to run

```bash
export SERPER_API_KEY="your-key-here"        # PowerShell: $env:SERPER_API_KEY = "your-key-here"
./mvnw package
java -jar target/serp-miner.jar                                                        # GUI
java -jar target/serp-miner.jar "crime reporting system" --mode=features               # task 1
java -jar target/serp-miner.jar "deep learning models journal paper" --mode=headings    # task 2
```

## 9. Suggested next steps

1. Run a real query in the GUI and confirm: progress messages update live, the query header appears, per-page summaries in the card list read sensibly, and links open correctly in your default browser.
2. Click through the JavaFX GUI end-to-end (query → mode → Run → watch progress → Summary tab, SERP Results cards, chart) to confirm layout and wrapping look right, not just that it launches.
3. Decide whether the grading rubric strictly requires CSV files on disk — if so, CSV writing can be reinstated alongside the card list (they aren't mutually exclusive).
4. Consider tuning `FeatureExtractor`'s `minSystemCount`/`maxResults` defaults after seeing real phrase-mining output — dynamic mining may surface more noise than the old fixed lexicon did on some topics.
