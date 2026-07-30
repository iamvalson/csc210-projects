# SERP Miner

Multithreaded Java program that takes a search query, pulls the SERP (Search
Engine Results Page) via the [Serper](https://serper.dev) API, fetches each
result page concurrently, and mines it into a single non-AI extractive
summary plus a ranked, visualized breakdown. No LLM/ML calls anywhere -
extraction is dynamic frequency/HTML-structure based (no topic-specific
keyword list) and summarization is a word-frequency (Luhn-style) algorithm.

Two modes select what gets ranked and charted:

- `features` - dynamically mines 2- and 3-word phrases from the fetched page
  text (skipping stopwords, favoring longer phrases over their subsumed
  sub-phrases) and ranks them by how many distinct source pages ("systems")
  contain each one. Works for any topic - there's no crime-reporting-specific
  lexicon anymore.
- `headings` - collects the distinct sub-headings (`h1`-`h4`) across fetched
  paper pages (e.g. deep-learning journal papers), ranked by how many pages
  use each one.

The JavaFX GUI shows each SERP result as a readable card (title, clickable
link, and a short 1-2 sentence per-page summary underneath - generated the
same non-AI extractive way as the combined summary, falling back to Serper's
snippet if a page is too short to summarize), alongside the ranked chart and
combined summary tab. No CSV files are written; everything is browsable in
the app. The CLI prints the same per-result title/link/summary as plain text
(links aren't clickable from a terminal) and streams live progress messages
as it runs.

## Setup

1. Get a Serper API key from https://serper.dev (you already have an account).
2. Set it as an environment variable (never hardcode it):
   - PowerShell: `$env:SERPER_API_KEY = "your-key-here"`
   - bash: `export SERPER_API_KEY="your-key-here"`
3. Build the shaded jar:
   ```
   ./mvnw package
   ```
   (`mvnw`/`mvnw.cmd` download Maven automatically - no system Maven install required.)

## Running

**GUI** (JavaFX): query field, mode dropdown, a header showing the query you last ran, a spinner + live status text while pages are fetched concurrently ("Fetching pages (3/10)...", etc.), then a SERP-Results card list (clickable links + per-page summaries), a Summary tab, and the embedded bar chart:
```
java -jar target/serp-miner.jar
```

**CLI**:
```
java -jar target/serp-miner.jar "crime reporting system" --mode=features --num=10 --out=features.png
java -jar target/serp-miner.jar "deep learning models journal paper" --mode=headings --num=10 --out=headings.png
```

Flags (all optional except the query):
| Flag | Default | Meaning |
|---|---|---|
| `--mode` | `features` | `features` or `headings` |
| `--num` | `10` | Number of SERP results to fetch |
| `--threads` | `8` | Thread-pool size for concurrent page fetching |
| `--out` | `chart.png` | Where to save the bar chart |

## Architecture

```
serp/api        SerperClient          - calls google.serper.dev/search, parses organic results
serp/model      SearchResult, PageContent, PageSummary, FeatureCount, HeadingEntry, Ranked (+ sourceLinks())
serp/fetch      PageFetcher           - ExecutorService + CompletableFuture, per-page timeout, failures skipped,
                                        reports live "x of y fetched" progress via a callback
serp/extract    FeatureExtractor      - dynamic 2/3-word phrase mining -> distinct-source counts, no fixed lexicon
                HeadingExtractor      - h1-h4 dedup across pages -> occurrence counts
serp/text       TextTokenizer         - shared stopword/significant-word logic (used by summarizer + extractor)
serp/summarize  ExtractiveSummarizer  - word-frequency sentence scoring (no AI); used both for the combined
                                        summary and for each page's short 1-2 sentence summary
serp/viz        ChartRenderer         - JFreeChart bar chart PNG (CLI path only)
serp/app        Pipeline, Cli, Main   - orchestration + entry points; Pipeline reports per-stage progress via a
                                        Consumer<String> callback so both CLI and GUI can show live status
serp/gui        MinerApp              - JavaFX front-end: query header, ProgressIndicator + live status label,
                                        SERP-Results card ListView (clickable Hyperlink cells via HostServices,
                                        wrapped per-page summary text), Summary tab, embedded BarChart;
                                        javafx.concurrent.Task keeps network I/O off the FX thread
```

## Tests

```
./mvnw test
```

Covers `FeatureExtractor`, `HeadingExtractor`, and `ExtractiveSummarizer`
against synthetic fixtures (no network access needed).

## Platform note

The shaded jar bundles JavaFX with the `win` classifier (see `pom.xml`), so
`target/serp-miner.jar` is Windows-only as built. See `PROJECT_STATUS.md` for
the cross-platform tradeoff.
