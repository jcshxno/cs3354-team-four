/*
Module Name: InfoResponse
Date Written: 2026-11-08
Author: Joshua Ayeh-Kwapong
Module Purpose: JSON body returned by GET /info (size of the loaded graph).
*/
package edu.utdallas.northstar.spike;

public record InfoResponse(int nodes, int edges, int routableNodes) { }
