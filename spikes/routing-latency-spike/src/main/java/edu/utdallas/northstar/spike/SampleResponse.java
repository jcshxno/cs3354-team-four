/*
Module Name: SampleResponse
Date Written: 2026-11-08
Author: Joshua Ayeh-Kwapong
Module Purpose: JSON body returned by GET /sample (routable start/end pairs).
*/
package edu.utdallas.northstar.spike;

public record SampleResponse(int[][] pairs) { }
