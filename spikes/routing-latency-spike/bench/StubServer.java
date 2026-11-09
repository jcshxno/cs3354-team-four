/*
Module Name: StubServer
Date Written: 2026-11-08
Author: Joshua Ayeh-Kwapong
Module Purpose: TEST DOUBLE used only to check that Benchmark.java works. It
    speaks the same HTTP/JSON as the Spring Boot app and reuses the real
    RoutingService, but reads the graph from memory instead of PostgreSQL.
    Its numbers are NOT the spike result. --fake-db-ms adds artificial database
    delay so the benchmark's FAIL verdict can be exercised.
Run: java -cp target/classes bench/StubServer.java [--port 18080] [--width 100] [--height 100] [--fake-db-ms 0]
*/
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import edu.utdallas.northstar.spike.EdgeList;
import edu.utdallas.northstar.spike.GridGraphGenerator;
import edu.utdallas.northstar.spike.RouteResponse;
import edu.utdallas.northstar.spike.RoutingService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

public class StubServer {
    private static final long SEED = 42;
    private static final int HTTP_OK = 200;
    private static final int HTTP_BAD_REQUEST = 400;
    private static final int WORKER_THREADS = 4;

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(option(args, "--port", "18080"));
        int width = Integer.parseInt(option(args, "--width", "100"));
        int height = Integer.parseInt(option(args, "--height", "100"));
        long fakeDbMs = Long.parseLong(option(args, "--fake-db-ms", "0"));

        EdgeList edgeList = GridGraphGenerator.generate(width, height, SEED);
        RoutingService service = new RoutingService(() -> {
            sleep(fakeDbMs);
            return edgeList;
        });
        service.loadCache();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(WORKER_THREADS));
        server.createContext("/info", exchange -> {
            var info = service.info();
            send(exchange, HTTP_OK, String.format(Locale.ROOT,
                    "{\"nodes\":%d,\"edges\":%d,\"routableNodes\":%d}", info.nodes(), info.edges(), info.routableNodes()));
        });
        server.createContext("/sample", exchange -> {
            Map<String, String> query = query(exchange);
            int[][] pairs = service.sample(Integer.parseInt(query.getOrDefault("count", "100")),
                    Long.parseLong(query.getOrDefault("seed", "1")), query.getOrDefault("mode", "random")).pairs();
            StringBuilder json = new StringBuilder("{\"pairs\":[");
            for (int i = 0; i < pairs.length; i++) {
                json.append(i == 0 ? "" : ",").append('[').append(pairs[i][0]).append(',').append(pairs[i][1]).append(']');
            }
            send(exchange, HTTP_OK, json.append("]}").toString());
        });
        server.createContext("/route", exchange -> {
            Map<String, String> query = query(exchange);
            try {
                RouteResponse r = service.route(RoutingService.Engine.parse(query.getOrDefault("engine", "cached")),
                        Integer.parseInt(query.get("from")), Integer.parseInt(query.get("to")));
                send(exchange, HTTP_OK, String.format(Locale.ROOT,
                        "{\"found\":%b,\"engine\":\"%s\",\"from\":%d,\"to\":%d,\"distanceMeters\":%.3f,"
                                + "\"estimatedSeconds\":%.3f,\"pathNodeCount\":%d,\"path\":%s,\"settledNodes\":%d,"
                                + "\"loadMs\":%.3f,\"computeMs\":%.3f,\"serverMs\":%.3f}",
                        r.found(), r.engine(), r.from(), r.to(), r.distanceMeters(), r.estimatedSeconds(),
                        r.pathNodeCount(), Arrays.toString(r.path()).replace(" ", ""), r.settledNodes(),
                        r.loadMs(), r.computeMs(), r.serverMs()));
            } catch (IllegalArgumentException e) {
                send(exchange, HTTP_BAD_REQUEST, "{}");
            }
        });
        server.start();
        System.out.println("StubServer listening on port " + port + " (fake DB delay " + fakeDbMs + " ms)");
    }

    private static Map<String, String> query(HttpExchange exchange) {
        Map<String, String> values = new HashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw != null) {
            for (String part : raw.split("&")) {
                String[] pair = part.split("=", 2);
                values.put(pair[0], pair.length > 1 ? pair[1] : "");
            }
        }
        return values;
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String option(String[] args, String name, String fallback) {
        for (int i = 0; i + 1 < args.length; i++) {
            if (args[i].equals(name)) {
                return args[i + 1];
            }
        }
        return fallback;
    }
}
