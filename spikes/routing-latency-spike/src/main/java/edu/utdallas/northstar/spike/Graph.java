/*
Module Name: Graph
Date Written: 2026-11-08
Author: Joshua Ayeh-Kwapong
Module Purpose: Immutable in-memory campus graph in compressed adjacency form
    (CSR). Each undirected edge is expanded into two directed entries so a
    route search can walk neighbours with a simple array scan.
*/
package edu.utdallas.northstar.spike;

import java.util.Arrays;

public final class Graph {
    private static final int UNVISITED = -1;

    private final int nodeCount;
    private final double[] xMeters;
    private final double[] yMeters;
    private final int[] offsets;
    private final int[] targets;
    private final double[] costs;
    private int[] largestComponentNodes;

    private Graph(int nodeCount, double[] xMeters, double[] yMeters,
                  int[] offsets, int[] targets, double[] costs) {
        this.nodeCount = nodeCount;
        this.xMeters = xMeters;
        this.yMeters = yMeters;
        this.offsets = offsets;
        this.targets = targets;
        this.costs = costs;
    }

    public static Graph from(EdgeList edgeList) {
        int nodeCount = edgeList.nodeCount();
        int edgeCount = edgeList.edgeCount();
        int[] sources = edgeList.source();
        int[] edgeTargets = edgeList.target();
        double[] edgeCosts = edgeList.cost();

        // Count the degree of every node, shifted by one so a prefix sum gives offsets.
        int[] offsets = new int[nodeCount + 1];
        for (int i = 0; i < edgeCount; i++) {
            if (sources[i] < 0 || sources[i] >= nodeCount
                    || edgeTargets[i] < 0 || edgeTargets[i] >= nodeCount) {
                throw new IllegalArgumentException(
                        "Edge " + i + " references a node outside 0.." + (nodeCount - 1));
            }
            offsets[sources[i] + 1]++;
            offsets[edgeTargets[i] + 1]++;
        }
        for (int i = 0; i < nodeCount; i++) {
            offsets[i + 1] += offsets[i];
        }

        // Fill both directions of every edge.
        int[] nextSlot = Arrays.copyOf(offsets, nodeCount);
        int[] targets = new int[2 * edgeCount];
        double[] costs = new double[2 * edgeCount];
        for (int i = 0; i < edgeCount; i++) {
            int s = sources[i];
            int t = edgeTargets[i];
            targets[nextSlot[s]] = t;
            costs[nextSlot[s]++] = edgeCosts[i];
            targets[nextSlot[t]] = s;
            costs[nextSlot[t]++] = edgeCosts[i];
        }
        return new Graph(nodeCount, edgeList.x(), edgeList.y(), offsets, targets, costs);
    }

    public int nodeCount() { return nodeCount; }
    public int edgeCount() { return targets.length / 2; }
    public double x(int node) { return xMeters[node]; }
    public double y(int node) { return yMeters[node]; }
    int[] offsets() { return offsets; }
    int[] targets() { return targets; }
    double[] costs() { return costs; }

    /** Nodes of the biggest connected piece; benchmark pairs are drawn from it so every pair is routable. */
    public synchronized int[] largestComponentNodes() {
        if (largestComponentNodes == null) {
            largestComponentNodes = computeLargestComponent();
        }
        return largestComponentNodes;
    }

    private int[] computeLargestComponent() {
        int[] componentOf = new int[nodeCount];
        Arrays.fill(componentOf, UNVISITED);
        int[] queue = new int[nodeCount];
        int bestComponent = UNVISITED;
        int bestSize = 0;
        int componentCount = 0;

        for (int start = 0; start < nodeCount; start++) {
            if (componentOf[start] != UNVISITED) {
                continue;
            }
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            componentOf[start] = componentCount;
            while (head < tail) {
                int node = queue[head++];
                for (int e = offsets[node]; e < offsets[node + 1]; e++) {
                    int next = targets[e];
                    if (componentOf[next] == UNVISITED) {
                        componentOf[next] = componentCount;
                        queue[tail++] = next;
                    }
                }
            }
            if (tail > bestSize) {
                bestSize = tail;
                bestComponent = componentCount;
            }
            componentCount++;
        }

        int[] members = new int[bestSize];
        int filled = 0;
        for (int node = 0; node < nodeCount; node++) {
            if (componentOf[node] == bestComponent) {
                members[filled++] = node;
            }
        }
        return members;
    }
}
