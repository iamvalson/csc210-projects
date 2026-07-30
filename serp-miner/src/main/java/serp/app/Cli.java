package serp.app;

import serp.model.PageSummary;
import serp.model.Ranked;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Command-line entry point: `java -jar serp-miner.jar "<query>" [--mode=...] [--num=...]` */
public final class Cli {

    private Cli() {
    }

    public static void run(String[] args) throws Exception {
        Map<String, String> options = parseOptions(args);
        String query = options.get("query");
        if (query == null || query.isBlank()) {
            System.err.println("Usage: java -jar serp-miner.jar \"<query>\" [--mode=features|headings] [--num=10] [--threads=8] [--out=chart.png]");
            System.exit(1);
            return;
        }

        String apiKey = options.getOrDefault("api-key", System.getenv("SERPER_API_KEY"));
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Missing Serper API key. Set the SERPER_API_KEY environment variable.");
            System.exit(1);
            return;
        }

        Mode mode = Mode.fromFlag(options.getOrDefault("mode", "features"));
        int numResults = Integer.parseInt(options.getOrDefault("num", "10"));
        int threads = Integer.parseInt(options.getOrDefault("threads", "8"));
        String outputPath = options.getOrDefault("out", "chart.png");

        Pipeline pipeline = new Pipeline(apiKey, threads);
        PipelineResult result = pipeline.run(query, mode, numResults, System.out::println);

        System.out.println();
        System.out.println("SERP results:");
        for (PageSummary pageSummary : result.pageSummaries()) {
            System.out.println("  " + pageSummary.title());
            System.out.println("    " + pageSummary.link());
            System.out.println("    " + pageSummary.summary());
            System.out.println();
        }

        System.out.println(result.resultLabel() + ":");
        for (Ranked item : result.ranked()) {
            System.out.printf("  %-45s %d%n", item.label(), item.count());
        }

        System.out.println();
        System.out.println("Summary:");
        System.out.println(result.summary());

        Path outFile = Path.of(outputPath);
        pipeline.chartRenderer().saveAsPng(result.chart(), outFile);
        System.out.println();
        System.out.println("Chart saved to " + outFile.toAbsolutePath());
    }

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        List<String> positional = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                String[] parts = arg.substring(2).split("=", 2);
                options.put(parts[0], parts.length > 1 ? parts[1] : "true");
            } else {
                positional.add(arg);
            }
        }
        if (!positional.isEmpty()) {
            options.put("query", String.join(" ", positional));
        }
        return options;
    }
}
