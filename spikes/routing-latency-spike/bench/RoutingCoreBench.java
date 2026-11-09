/*
Module Name: RoutingCoreBench
Date Written: 2026-11-08
Author: Joshua Ayeh-Kwapong
Module Purpose: Times ONLY the in-memory route search (no HTTP, no database) at
    several graph sizes. It answers "is the algorithm itself a threat to the 3 s
    budget?" and shows how much of the budget is left for the database and web layers.
    It does NOT answer the full Spring Boot + PostgreSQL question; see Benchmark.java.
Run: java -cp target/classes bench/RoutingCoreBench.java   (from the project root)
*/
import edu.utdallas.northstar.spike.DijkstraRouter;
import edu.utdallas.northstar.spike.EdgeList;
import edu.utdallas.northstar.spike.Graph;
import edu.utdallas.northstar.spike.GridGraphGenerator;
import edu.utdallas.northstar.spike.PairSampler;
import edu.utdallas.northstar.spike.RouteResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

public class RoutingCoreBench {
    private static final int[][] GRID_SIZES = {{32, 32}, {100, 100}, {316, 316}, {707, 707}};
    private static final int JIT_WARMUP_QUERIES = 300;
    private static final int WARMUP_QUERIES = 30;
    private static final int TIMED_QUERIES = 200;
    private static final long SEED = 42;
    private static final double BUDGET_MS = 3000.0;
    private static final double NANOS_PER_MILLI = 1_000_000.0;
    private static final String START_MARKER = "<!-- CORE-RESULTS:START -->";
    private static final String END_MARKER = "<!-- CORE-RESULTS:END -->";

    public static void main(String[] args) throws Exception {
        Path outFile = Path.of(option(args, "--out", "results/core.md"));
        Path readme = Path.of(option(args, "--readme", "README.md"));

        // Warm up the JIT on a small graph so compilation time is not charged to real measurements.
        Graph warmGraph = Graph.from(GridGraphGenerator.generate(GRID_SIZES[0][0], GRID_SIZES[0][1], SEED));
        for (int[] pair : PairSampler.sample(warmGraph, JIT_WARMUP_QUERIES, SEED, PairSampler.MODE_RANDOM)) {
            DijkstraRouter.route(warmGraph, pair[0], pair[1]);
        }

        StringBuilder table = new StringBuilder();
        table.append("| Nodes | Edges | Build graph (ms) | Pair mode | p50 (ms) | p95 (ms) | max (ms) | Avg nodes searched | Max as % of 3 s |\n");
        table.append("|---:|---:|---:|---|---:|---:|---:|---:|---:|\n");
        double worstMax = 0.0;

        for (int[] size : GRID_SIZES) {
            EdgeList edges = GridGraphGenerator.generate(size[0], size[1], SEED);
            long buildStart = System.nanoTime();
            Graph graph = Graph.from(edges);
            double buildMs = (System.nanoTime() - buildStart) / NANOS_PER_MILLI;

            for (String mode : new String[]{PairSampler.MODE_RANDOM, PairSampler.MODE_FAR}) {
                int[][] pairs = PairSampler.sample(graph, WARMUP_QUERIES + TIMED_QUERIES, SEED + 1, mode);
                for (int i = 0; i < WARMUP_QUERIES; i++) {
                    DijkstraRouter.route(graph, pairs[i][0], pairs[i][1]);
                }
                double[] millis = new double[TIMED_QUERIES];
                long searched = 0;
                for (int i = 0; i < TIMED_QUERIES; i++) {
                    int[] pair = pairs[WARMUP_QUERIES + i];
                    long start = System.nanoTime();
                    RouteResult result = DijkstraRouter.route(graph, pair[0], pair[1]);
                    millis[i] = (System.nanoTime() - start) / NANOS_PER_MILLI;
                    searched += result.settledNodes();
                    if (!result.found()) {
                        throw new IllegalStateException("Sampled pair was not routable: " + Arrays.toString(pair));
                    }
                }
                Arrays.sort(millis);
                double max = millis[TIMED_QUERIES - 1];
                worstMax = Math.max(worstMax, max);
                table.append(String.format("| %,d | %,d | %.1f | %s | %.2f | %.2f | %.2f | %,d | %.2f%% |%n",
                        graph.nodeCount(), graph.edgeCount(), buildMs, mode,
                        percentile(millis, 50), percentile(millis, 95), max,
                        searched / TIMED_QUERIES, 100.0 * max / BUDGET_MS));
            }
            System.out.println("done " + graph.nodeCount() + " nodes");
        }

        String report = "**Routing core only (in memory; no HTTP, no PostgreSQL).** "
                + LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) + " | Java "
                + System.getProperty("java.version") + " | " + System.getProperty("os.name") + " "
                + System.getProperty("os.arch") + " | " + Runtime.getRuntime().availableProcessors()
                + " CPU(s) | max heap " + Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MB\n\n"
                + TIMED_QUERIES + " timed searches per row after " + WARMUP_QUERIES + " warm-up; "
                + "\"far\" = opposite corners of the map (worst case for Dijkstra). "
                + "Synthetic grid graph, seed " + SEED + ".\n\n"
                + table
                + String.format("%nSlowest single search in this run: %.2f ms (%.2f%% of the 3000 ms budget).%n",
                worstMax, 100.0 * worstMax / BUDGET_MS);

        write(outFile, report);
        replaceBetween(readme, START_MARKER, END_MARKER, report);
        System.out.println(report);
    }

    /** Nearest-rank percentile of an ascending-sorted array. */
    static double percentile(double[] sorted, double percent) {
        int rank = (int) Math.ceil(percent / 100.0 * sorted.length);
        return sorted[Math.max(0, rank - 1)];
    }

    static String option(String[] args, String name, String fallback) {
        for (int i = 0; i + 1 < args.length; i++) {
            if (args[i].equals(name)) {
                return args[i + 1];
            }
        }
        return fallback;
    }

    static void write(Path file, String text) throws Exception {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, text);
    }

    static void replaceBetween(Path file, String startMarker, String endMarker, String replacement) throws Exception {
        if (!Files.exists(file)) {
            System.out.println("(README not found at " + file + "; skipped updating it)");
            return;
        }
        String text = Files.readString(file);
        int start = text.indexOf(startMarker);
        int end = text.indexOf(endMarker);
        if (start < 0 || end < start) {
            System.out.println("(markers not found in " + file + "; skipped updating it)");
            return;
        }
        Files.writeString(file, text.substring(0, start + startMarker.length()) + "\n"
                + replacement + "\n" + text.substring(end));
    }
}
