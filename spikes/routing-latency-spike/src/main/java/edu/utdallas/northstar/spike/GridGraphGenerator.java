/*
Module Name: GridGraphGenerator
Date Written: 2026-11-08
Author: Joshua Ayeh-Kwapong
Module Purpose: Builds a SYNTHETIC campus-like walkway graph (a street grid with
    some blocked edges and uneven path lengths). Real UTD path data is not
    available yet (assumption A1), so this stands in to test latency at scale.
    The same seed always produces the same graph on every machine.
*/
package edu.utdallas.northstar.spike;

import java.util.Arrays;
import java.util.Random;

public final class GridGraphGenerator {
    public static final double SPACING_METERS = 10.0;
    public static final double EDGE_DROP_PROBABILITY = 0.10;
    public static final double COST_JITTER_FRACTION = 0.25;
    private static final int MIN_SIDE = 2;

    private GridGraphGenerator() { }

    public static EdgeList generate(int width, int height, long seed) {
        if (width < MIN_SIDE || height < MIN_SIDE) {
            throw new IllegalArgumentException("Grid must be at least " + MIN_SIDE + "x" + MIN_SIDE);
        }
        int nodeCount = width * height;
        Random random = new Random(seed);

        double[] x = new double[nodeCount];
        double[] y = new double[nodeCount];
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int id = row * width + col;
                x[id] = col * SPACING_METERS;
                y[id] = row * SPACING_METERS;
            }
        }

        int maxEdges = (width - 1) * height + width * (height - 1);
        int[] source = new int[maxEdges];
        int[] target = new int[maxEdges];
        double[] cost = new double[maxEdges];
        int edgeCount = 0;

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int id = row * width + col;
                if (col + 1 < width && random.nextDouble() >= EDGE_DROP_PROBABILITY) {
                    source[edgeCount] = id;
                    target[edgeCount] = id + 1;
                    cost[edgeCount++] = SPACING_METERS * (1.0 + random.nextDouble() * COST_JITTER_FRACTION);
                }
                if (row + 1 < height && random.nextDouble() >= EDGE_DROP_PROBABILITY) {
                    source[edgeCount] = id;
                    target[edgeCount] = id + width;
                    cost[edgeCount++] = SPACING_METERS * (1.0 + random.nextDouble() * COST_JITTER_FRACTION);
                }
            }
        }
        return new EdgeList(nodeCount, x, y,
                Arrays.copyOf(source, edgeCount),
                Arrays.copyOf(target, edgeCount),
                Arrays.copyOf(cost, edgeCount));
    }
}
