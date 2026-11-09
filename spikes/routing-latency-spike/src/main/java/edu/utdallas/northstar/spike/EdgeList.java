/*
Module Name: EdgeList
Date Written: 2026-11-08
Author: Joshua Ayeh-Kwapong
Module Purpose: Plain edge-list form of the campus graph, exactly as stored in
    PostgreSQL (each undirected edge is stored once).
*/
package edu.utdallas.northstar.spike;

public record EdgeList(int nodeCount, double[] x, double[] y,
                       int[] source, int[] target, double[] cost) {

    public int edgeCount() {
        return source.length;
    }
}
