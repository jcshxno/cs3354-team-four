/*
Module Name: RouteController
Date Written: 2026-11-08
Author: Joshua Ayeh-Kwapong
Module Purpose: HTTP endpoints for the spike: /route (the thing QR-01 times),
    /info and /sample (used by the benchmark client).
*/
package edu.utdallas.northstar.spike.web;

import edu.utdallas.northstar.spike.InfoResponse;
import edu.utdallas.northstar.spike.RouteResponse;
import edu.utdallas.northstar.spike.RoutingService;
import edu.utdallas.northstar.spike.SampleResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RouteController {
    private final RoutingService routingService;

    public RouteController(RoutingService routingService) {
        this.routingService = routingService;
    }

    @GetMapping("/route")
    public ResponseEntity<RouteResponse> route(@RequestParam("from") int from,
                                               @RequestParam("to") int to,
                                               @RequestParam(name = "engine", defaultValue = "cached") String engine) {
        try {
            return ResponseEntity.ok(routingService.route(RoutingService.Engine.parse(engine), from, to));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/info")
    public InfoResponse info() {
        return routingService.info();
    }

    @GetMapping("/sample")
    public SampleResponse sample(@RequestParam(name = "count", defaultValue = "100") int count,
                                 @RequestParam(name = "mode", defaultValue = "random") String mode,
                                 @RequestParam(name = "seed", defaultValue = "1") long seed) {
        return routingService.sample(count, seed, mode);
    }
}
