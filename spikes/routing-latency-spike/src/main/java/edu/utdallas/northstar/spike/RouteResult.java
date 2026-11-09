/*
Module Name: RouteResult
Date Written: 2026-11-08
Author: Joshua Ayeh-Kwapong
Module Purpose: Outcome of one shortest-path search.
*/
package edu.utdallas.northstar.spike;

public record RouteResult(boolean found, double distanceMeters, int[] path, int settledNodes) { }
