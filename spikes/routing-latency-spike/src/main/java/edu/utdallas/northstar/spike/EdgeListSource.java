/*
Module Name: EdgeListSource
Date Written: 2026-11-08
Author: Joshua Ayeh-Kwapong
Module Purpose: Where the graph comes from. GraphRepository implements this with
    PostgreSQL; the benchmark test double implements it with memory.
*/
package edu.utdallas.northstar.spike;

public interface EdgeListSource {
    EdgeList loadEdgeList();
}
