/*
Module Name: RouteResponse
Date Written: 2026-11-08
Author: Joshua Ayeh-Kwapong
Module Purpose: JSON body returned by GET /route. Includes server-side timing
    so a benchmark can show where the time goes (database vs. computation).
*/
package edu.utdallas.northstar.spike;

public record RouteResponse(boolean found, String engine, int from, int to,
                            double distanceMeters, double estimatedSeconds,
                            int pathNodeCount, int[] path, int settledNodes,
                            double loadMs, double computeMs, double serverMs) { }
