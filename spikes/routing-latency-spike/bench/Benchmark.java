/*
Module Name: Benchmark
Date Written: 2026-11-08
Author: Joshua Ayeh-Kwapong
Module Purpose: End-to-end latency test of the running Spring Boot + PostgreSQL
    service over real HTTP. For each architecture ("engine") and each pair mode it
    sends routable start/end pairs to GET /route, records client-side latency
    (request sent -> full JSON received), and checks requirement QR-01: every
    route must be displayed within 3 seconds. Results are appended to
    results/e2e.md and copied into the README results section.
Run: java bench/Benchmark.java [--base http://localhost:8080] [--requests 100]
         [--warmup 10] [--concurrency 1] [--engines cached,per_request]
         [--modes random,far] [--label text]
*/
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Benchmark {
    private static final double BUDGET_MS = 3000.0;
    private static final double NANOS_PER_MILLI = 1_000_000.0;
    private static final int CONNECT_TIMEOUT_SECONDS = 5;
    private static final int REQUEST_TIMEOUT_SECONDS = 120;
    private static final int HTTP_OK = 200;
    private static final long PAIR_SEED = 7;
    private static final String RESULTS_HEADER = "### End-to-end results (real HTTP requests to the running service)\n";
    private static final String START_MARKER = "<!-- E2E-RESULTS:START -->";
    private static final String END_MARKER = "<!-- E2E-RESULTS:END -->";
    private static final Pattern PAIR = Pattern.compile("\\[\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\]");

    /** One timed request. */
    record Sample(double latencyMs, boolean routeReturned, double loadMs, double computeMs) { }

    public static void main(String[] args) throws Exception {
        String base = option(args, "--base", "http://localhost:8080");
        int requests = Integer.parseInt(option(args, "--requests", "100"));
        int warmup = Integer.parseInt(option(args, "--warmup", "10"));
        int concurrency = Integer.parseInt(option(args, "--concurrency", "1"));
        String[] engines = option(args, "--engines", "cached,per_request").split(",");
        String[] modes = option(args, "--modes", "random,far").split(",");
        Path outFile = Path.of(option(args, "--out", "results/e2e.md"));
        Path readme = Path.of(option(args, "--readme", "README.md"));

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();

        String info;
        try {
            info = get(client, base + "/info");
        } catch (Exception e) {
            System.err.println("Could not reach " + base + "/info -> " + e);
            System.err.println("Is the Spring Boot app running and finished starting (and is the database up)?");
            System.exit(1);
            return;
        }
        long nodes = (long) number(info, "nodes");
        long edges = (long) number(info, "edges");
        String label = option(args, "--label", String.format(Locale.ROOT, "%,d nodes / %,d edges", nodes, edges));

        StringBuilder table = new StringBuilder();
        table.append("| Engine | Pair mode | Routes returned | p50 (ms) | p95 (ms) | p99 (ms) | max (ms) | Avg DB read + build (ms) | Avg search (ms) | QR-01: every request <= 3000 ms |\n");
        table.append("|---|---|---:|---:|---:|---:|---:|---:|---:|---|\n");
        double firstRouteMs = -1.0;
        boolean everythingPassed = true;

        for (String engine : engines) {
            for (String mode : modes) {
                int[][] pairs = parsePairs(get(client, base + "/sample?count=" + (warmup + requests)
                        + "&mode=" + mode + "&seed=" + PAIR_SEED));
                for (int i = 0; i < warmup; i++) {
                    Sample sample = routeOnce(client, base, engine, pairs[i]);
                    if (firstRouteMs < 0) {
                        firstRouteMs = sample.latencyMs();
                    }
                }
                List<Sample> samples = timedRun(client, base, engine,
                        Arrays.copyOfRange(pairs, warmup, warmup + requests), concurrency);
                String verdict = summarize(table, engine, mode, samples);
                everythingPassed &= verdict.startsWith("PASS");
                System.out.println(engine + " / " + mode + " -> " + verdict);
            }
        }

        String section = "#### " + label + " | " + LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) + "\n\n"
                + base + " | " + warmup + " warm-up + " + requests + " timed requests per row | concurrency "
                + concurrency + " | first /route after start (cold): "
                + (firstRouteMs < 0 ? "n/a (no warm-up)" : String.format(Locale.ROOT, "%.0f ms", firstRouteMs))
                + "\n\n" + table;

        String existing = Files.exists(outFile) ? Files.readString(outFile) : "";
        String combined = existing.isEmpty() ? RESULTS_HEADER + "\n" + section : existing + "\n" + section;
        if (outFile.getParent() != null) {
            Files.createDirectories(outFile.getParent());
        }
        Files.writeString(outFile, combined);
        replaceBetween(readme, START_MARKER, END_MARKER, combined);
        System.out.println("\n" + section);
        System.out.println(everythingPassed ? "OVERALL: QR-01 met in every row of this run."
                : "OVERALL: QR-01 NOT met in at least one row of this run.");
    }

    private static List<Sample> timedRun(HttpClient client, String base, String engine,
                                         int[][] pairs, int concurrency) throws Exception {
        List<Sample> samples = new ArrayList<>();
        if (concurrency <= 1) {
            for (int[] pair : pairs) {
                samples.add(routeOnce(client, base, engine, pair));
            }
            return samples;
        }
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        try {
            List<Callable<Sample>> tasks = new ArrayList<>();
            for (int[] pair : pairs) {
                tasks.add(() -> routeOnce(client, base, engine, pair));
            }
            for (Future<Sample> future : pool.invokeAll(tasks)) {
                samples.add(future.get());
            }
        } finally {
            pool.shutdown();
        }
        return samples;
    }

    private static Sample routeOnce(HttpClient client, String base, String engine, int[] pair) {
        String url = base + "/route?from=" + pair[0] + "&to=" + pair[1] + "&engine=" + engine;
        long start = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            double latency = (System.nanoTime() - start) / NANOS_PER_MILLI;
            boolean returned = response.statusCode() == HTTP_OK && response.body().contains("\"found\":true");
            return new Sample(latency, returned, number(response.body(), "loadMs"), number(response.body(), "computeMs"));
        } catch (Exception e) {
            return new Sample((System.nanoTime() - start) / NANOS_PER_MILLI, false, 0.0, 0.0);
        }
    }

    /** Appends one table row and returns the verdict text. */
    private static String summarize(StringBuilder table, String engine, String mode, List<Sample> samples) {
        int count = samples.size();
        double[] latencies = new double[count];
        int returned = 0;
        double loadSum = 0.0;
        double computeSum = 0.0;
        for (int i = 0; i < count; i++) {
            Sample sample = samples.get(i);
            latencies[i] = sample.latencyMs();
            if (sample.routeReturned()) {
                returned++;
                loadSum += sample.loadMs();
                computeSum += sample.computeMs();
            }
        }
        Arrays.sort(latencies);
        double max = latencies[count - 1];
        String verdict;
        if (returned < count) {
            verdict = "FAIL (" + (count - returned) + " of " + count + " requests returned no route)";
        } else if (max > BUDGET_MS) {
            verdict = String.format(Locale.ROOT, "FAIL (slowest %.0f ms)", max);
        } else {
            verdict = "PASS";
        }
        int divisor = Math.max(returned, 1);
        table.append(String.format(Locale.ROOT, "| %s | %s | %d/%d | %.1f | %.1f | %.1f | %.1f | %.2f | %.2f | %s |%n",
                engine, mode, returned, count,
                percentile(latencies, 50), percentile(latencies, 95), percentile(latencies, 99), max,
                loadSum / divisor, computeSum / divisor, verdict));
        return verdict;
    }

    /** Nearest-rank percentile of an ascending-sorted array. */
    static double percentile(double[] sorted, double percent) {
        int rank = (int) Math.ceil(percent / 100.0 * sorted.length);
        return sorted[Math.max(0, rank - 1)];
    }

    private static String get(HttpClient client, String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != HTTP_OK) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " from " + url);
        }
        return response.body();
    }

    private static double number(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*(-?[0-9.]+(?:[eE][+-]?[0-9]+)?)").matcher(json);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : 0.0;
    }

    private static int[][] parsePairs(String json) {
        List<int[]> pairs = new ArrayList<>();
        Matcher matcher = PAIR.matcher(json);
        while (matcher.find()) {
            pairs.add(new int[]{Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))});
        }
        return pairs.toArray(new int[0][]);
    }

    private static String option(String[] args, String name, String fallback) {
        for (int i = 0; i + 1 < args.length; i++) {
            if (args[i].equals(name)) {
                return args[i + 1];
            }
        }
        return fallback;
    }

    private static void replaceBetween(Path file, String startMarker, String endMarker, String replacement) throws Exception {
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
