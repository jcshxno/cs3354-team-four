/*
Module Name: RoutingService
Date Written: 2026-11-08
Author: Joshua Ayeh-Kwapong
Module Purpose: The two architectures under test.
    CACHED       - graph is read from PostgreSQL once at startup and kept in
                   memory; each request only runs the route search.
    PER_REQUEST  - every request re-reads the whole graph from PostgreSQL,
                   then runs the route search (the naive design).
*/
package edu.utdallas.northstar.spike;

public final class RoutingService {
    /** Walking speed used to turn distance into time (assumption A2 in P1). */
    public static final double WALKING_SPEED_METERS_PER_SECOND = 1.4;
    private static final double NANOS_PER_MILLI = 1_000_000.0;

    public enum Engine {
        CACHED, PER_REQUEST;

        public static Engine parse(String text) {
            String normalized = text.trim().toLowerCase().replace('-', '_');
            if (normalized.equals("cached")) {
                return CACHED;
            }
            if (normalized.equals("per_request")) {
                return PER_REQUEST;
            }
            throw new IllegalArgumentException("Unknown engine '" + text + "' (use cached or per_request)");
        }
    }

    private final EdgeListSource source;
    private volatile Graph cachedGraph;

    public RoutingService(EdgeListSource source) {
        this.source = source;
    }

    /** Reads the graph from the source once and keeps it in memory. */
    public synchronized void loadCache() {
        long start = System.nanoTime();
        cachedGraph = Graph.from(source.loadEdgeList());
        System.out.printf("Graph cached in memory: %d nodes, %d edges, loaded in %.0f ms%n",
                cachedGraph.nodeCount(), cachedGraph.edgeCount(), elapsedMs(start));
    }

    private Graph cached() {
        Graph graph = cachedGraph;
        if (graph == null) {
            synchronized (this) {
                if (cachedGraph == null) {
                    loadCache();
                }
                graph = cachedGraph;
            }
        }
        return graph;
    }

    public RouteResponse route(Engine engine, int from, int to) {
        long requestStart = System.nanoTime();

        Graph graph;
        double loadMs = 0.0;
        if (engine == Engine.CACHED) {
            graph = cached();
        } else {
            long loadStart = System.nanoTime();
            graph = Graph.from(source.loadEdgeList());
            loadMs = elapsedMs(loadStart);
        }

        long computeStart = System.nanoTime();
        RouteResult result = DijkstraRouter.route(graph, from, to);
        double computeMs = elapsedMs(computeStart);

        return new RouteResponse(result.found(), engine.name().toLowerCase(), from, to,
                result.distanceMeters(),
                result.distanceMeters() / WALKING_SPEED_METERS_PER_SECOND,
                result.path().length, result.path(), result.settledNodes(),
                loadMs, computeMs, elapsedMs(requestStart));
    }

    public InfoResponse info() {
        Graph graph = cached();
        return new InfoResponse(graph.nodeCount(), graph.edgeCount(), graph.largestComponentNodes().length);
    }

    public SampleResponse sample(int count, long seed, String mode) {
        return new SampleResponse(PairSampler.sample(cached(), count, seed, mode));
    }

    private static double elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / NANOS_PER_MILLI;
    }
}
