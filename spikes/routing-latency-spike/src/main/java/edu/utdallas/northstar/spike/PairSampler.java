/*
Module Name: PairSampler
Date Written: 2026-11-08
Author: Joshua Ayeh-Kwapong
Module Purpose: Chooses start/destination pairs for benchmarking. All pairs are
    routable (same connected piece). Mode "far" picks opposite corners of the
    map, which is the worst case for Dijkstra because it explores the most nodes.
*/
package edu.utdallas.northstar.spike;

import java.util.Random;

public final class PairSampler {
    public static final String MODE_RANDOM = "random";
    public static final String MODE_FAR = "far";
    private static final double FAR_BAND_FRACTION = 0.05;

    private PairSampler() { }

    public static int[][] sample(Graph graph, int count, long seed, String mode) {
        int[] nodes = graph.largestComponentNodes();
        Random random = new Random(seed);
        int[][] pairs = new int[count][2];

        int[] lowCorner = nodes;
        int[] highCorner = nodes;
        if (MODE_FAR.equals(mode)) {
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (int node : nodes) {
                double diagonal = graph.x(node) + graph.y(node);
                min = Math.min(min, diagonal);
                max = Math.max(max, diagonal);
            }
            double lowLimit = min + FAR_BAND_FRACTION * (max - min);
            double highLimit = max - FAR_BAND_FRACTION * (max - min);
            lowCorner = filter(graph, nodes, 0.0, lowLimit);
            highCorner = filter(graph, nodes, highLimit, Double.POSITIVE_INFINITY);
            if (lowCorner.length == 0 || highCorner.length == 0) {
                lowCorner = nodes;
                highCorner = nodes;
            }
        } else if (!MODE_RANDOM.equals(mode)) {
            throw new IllegalArgumentException("Unknown pair mode: " + mode);
        }

        for (int i = 0; i < count; i++) {
            int from = lowCorner[random.nextInt(lowCorner.length)];
            int to = highCorner[random.nextInt(highCorner.length)];
            while (to == from && nodes.length > 1) {
                to = highCorner[random.nextInt(highCorner.length)];
            }
            pairs[i][0] = from;
            pairs[i][1] = to;
        }
        return pairs;
    }

    private static int[] filter(Graph graph, int[] nodes, double minDiagonal, double maxDiagonal) {
        int kept = 0;
        int[] buffer = new int[nodes.length];
        for (int node : nodes) {
            double diagonal = graph.x(node) + graph.y(node);
            if (diagonal >= minDiagonal && diagonal <= maxDiagonal) {
                buffer[kept++] = node;
            }
        }
        return java.util.Arrays.copyOf(buffer, kept);
    }
}
