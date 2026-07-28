# SERP Miner

CSC 210 Concurrent Programming coursework. A multithreaded Java program that
queries a search engine, downloads the returned documents, summarises them, and
counts distinctive features and section sub-headings across the whole corpus.

The topic is **not** hard-coded. Crime reporting systems and deep learning
papers are shipped as example profiles; the user can search for anything and
supply their own profile.

## Requirements

- JDK 21 or newer (virtual threads are used)
- Maven 3.8+
- A Serper API key from https://serper.dev (2,500 free queries, no card)

## Setup

```bash
cp config.properties.example config.properties
# paste your Serper key into config.properties, or export SERPER_API_KEY
mvn clean package
```

`config.properties` is git-ignored. Never commit your key.

## Running

```bash
# default staged pipeline, crime reporting profile
java -jar target/serp-miner.jar --limit 40

# any topic, any profile
java -jar target/serp-miner.jar \
     --query "telemedicine platforms in Africa" \
     --profile profiles/crime-reporting.json \
     --limit 40 --mode staged

# deep learning sub-heading analysis
java -jar target/serp-miner.jar --profile profiles/deep-learning.json --limit 40

# offline demo, no network and no quota needed
java -jar target/serp-miner.jar --provider fixture --seeds seeds.txt

# full benchmark sweep, writes output/benchmark.csv and the speedup chart
java -jar target/serp-miner.jar --benchmark --repeats 3

# the deliberate race condition demonstration for the report
java -cp target/classes ng.edu.unilag.csc210.serpminer.bench.RaceConditionDemo
```

Outputs land in `output/`: `features.png`, `headings.png`, `speedup.png`,
`benchmark.csv` and `report.md`.

## Execution strategies

| Mode         | Description                                                |
| ------------ | ---------------------------------------------------------- |
| `sequential` | One thread. The baseline and the correctness oracle.       |
| `task`       | One task per document on a single fixed pool.              |
| `staged`     | Bounded queues between stages, separate I/O and CPU pools. |

## Concurrency features, and where to find each one

| Concept                                  | Class                                       |
| ---------------------------------------- | ------------------------------------------- |
| `ExecutorService`, fixed pools           | `TaskPerDocumentPipeline`                   |
| Virtual threads for I/O                  | `StagedPipeline`                            |
| Bounded `BlockingQueue` and backpressure | `StagedPipeline`                            |
| Poison pill termination                  | `StagedPipeline`                            |
| `CountDownLatch` as a phase barrier      | `TaskPerDocumentPipeline`, `StagedPipeline` |
| `ConcurrentHashMap` and `LongAdder`      | `ResultAggregator`                          |
| `ReentrantLock` per host                 | `HostRateLimiter`                           |
| Atomic file publication                  | `DiskCache`                                 |
| Safe publication after a barrier         | `Summarizer`                                |
| Named worker threads                     | `NamedThreadFactory`                        |
| Graceful shutdown                        | `TaskPerDocumentPipeline.shutdown`          |
| Deliberate race for comparison           | `RaceConditionDemo`                         |

## Two-phase summarisation

TF-IDF needs corpus-wide document frequencies, which cannot be known until
every document has been parsed. The pipeline therefore runs:

1. **Phase 1 (parallel)** every worker parses its document and merges its term
   set into a shared `ConcurrentHashMap<String, LongAdder>`. The map is
   contended and mutable here.
2. **Barrier** a `CountDownLatch` opens only when every document has
   contributed. This is the safe-publication point.
3. **Phase 2 (parallel)** every worker scores and summarises its own document.
   The map is now effectively immutable and is read with no locking at all.

## Notes for the write-up

- The `SearchProvider` interface has Serper, cached and offline-fixture
  implementations, so the SERP backend can be swapped with one flag.
- Publisher sites such as ScienceDirect, IEEE Xplore and ResearchGate return
  403 to programmatic clients. A failed fetch is an expected outcome that is
  counted, not an exception that kills a worker.
- Feature detection is keyword-based and has known precision limits. State this
  honestly rather than overclaiming.
- Every model type is a `record`, therefore immutable, therefore safe to pass
  across a queue without defensive copying.

## Testing

```bash
mvn test
```

`SummarizerTest` asserts that concurrent phase one produces the same corpus
statistics as a sequential run, which is the thread-safety proof.
