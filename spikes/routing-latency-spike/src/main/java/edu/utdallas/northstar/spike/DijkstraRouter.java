/*
Module Name: DijkstraRouter
Date Written: 2026-11-08
Author: Joshua Ayeh-Kwapong
Module Purpose: Shortest walking route between two nodes using Dijkstra's
    algorithm with an indexed binary heap and early exit at the destination.
    Deliberately NO A* heuristic: this is the plain worst-case baseline.
*/
package edu.utdallas.northstar.spike;

import java.util.Arrays;

public final class DijkstraRouter {
    private static final int NOT_IN_HEAP = -1;
    private static final int SETTLED = -2;
    private static final int NO_PREVIOUS = -1;

    private DijkstraRouter() { }

    public static RouteResult route(Graph graph, int from, int to) {
        int nodeCount = graph.nodeCount();
        if (from < 0 || from >= nodeCount || to < 0 || to >= nodeCount) {
            throw new IllegalArgumentException(
                    "Node ids must be in 0.." + (nodeCount - 1) + " (from=" + from + ", to=" + to + ")");
        }
        int[] offsets = graph.offsets();
        int[] targets = graph.targets();
        double[] costs = graph.costs();

        double[] dist = new double[nodeCount];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        int[] previous = new int[nodeCount];
        Arrays.fill(previous, NO_PREVIOUS);
        int[] heap = new int[nodeCount];
        int[] heapPos = new int[nodeCount];
        Arrays.fill(heapPos, NOT_IN_HEAP);

        int heapSize = 0;
        dist[from] = 0.0;
        heap[heapSize] = from;
        heapPos[from] = heapSize++;
        int settledCount = 0;

        while (heapSize > 0) {
            // Pop the closest unsettled node.
            int current = heap[0];
            heapSize--;
            heapPos[current] = SETTLED;
            if (heapSize > 0) {
                int last = heap[heapSize];
                heap[0] = last;
                heapPos[last] = 0;
                siftDown(heap, heapPos, dist, 0, heapSize);
            }
            settledCount++;
            if (current == to) {
                break;
            }

            // Relax every outgoing edge.
            for (int e = offsets[current]; e < offsets[current + 1]; e++) {
                int next = targets[e];
                if (heapPos[next] == SETTLED) {
                    continue;
                }
                double candidate = dist[current] + costs[e];
                if (candidate < dist[next]) {
                    dist[next] = candidate;
                    previous[next] = current;
                    if (heapPos[next] == NOT_IN_HEAP) {
                        heap[heapSize] = next;
                        heapPos[next] = heapSize;
                        siftUp(heap, heapPos, dist, heapSize);
                        heapSize++;
                    } else {
                        siftUp(heap, heapPos, dist, heapPos[next]);
                    }
                }
            }
        }

        if (heapPos[to] != SETTLED) {
            return new RouteResult(false, 0.0, new int[0], settledCount);
        }
        return new RouteResult(true, dist[to], buildPath(previous, from, to), settledCount);
    }

    private static int[] buildPath(int[] previous, int from, int to) {
        int length = 1;
        for (int node = to; node != from; node = previous[node]) {
            length++;
        }
        int[] path = new int[length];
        int index = length - 1;
        for (int node = to; ; node = previous[node]) {
            path[index--] = node;
            if (node == from) {
                break;
            }
        }
        return path;
    }

    private static void siftUp(int[] heap, int[] heapPos, double[] dist, int index) {
        int node = heap[index];
        double key = dist[node];
        while (index > 0) {
            int parentIndex = (index - 1) >>> 1;
            int parent = heap[parentIndex];
            if (dist[parent] <= key) {
                break;
            }
            heap[index] = parent;
            heapPos[parent] = index;
            index = parentIndex;
        }
        heap[index] = node;
        heapPos[node] = index;
    }

    private static void siftDown(int[] heap, int[] heapPos, double[] dist, int index, int size) {
        int node = heap[index];
        double key = dist[node];
        int half = size >>> 1;
        while (index < half) {
            int child = 2 * index + 1;
            if (child + 1 < size && dist[heap[child + 1]] < dist[heap[child]]) {
                child++;
            }
            if (key <= dist[heap[child]]) {
                break;
            }
            heap[index] = heap[child];
            heapPos[heap[index]] = index;
            index = child;
        }
        heap[index] = node;
        heapPos[node] = index;
    }
}
