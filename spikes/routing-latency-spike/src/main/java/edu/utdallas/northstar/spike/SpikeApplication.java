/*
Module Name: SpikeApplication
Date Written: 2026-11-08
Author: Joshua Ayeh-Kwapong
Module Purpose: Spring Boot entry point. On startup it makes sure PostgreSQL holds
    a synthetic campus graph of the requested size, then caches it in memory.
*/
package edu.utdallas.northstar.spike;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

@SpringBootApplication
public class SpikeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpikeApplication.class, args);
    }

    @Bean
    public GraphRepository graphRepository(DataSource dataSource) {
        return new GraphRepository(dataSource);
    }

    @Bean
    public RoutingService routingService(GraphRepository repository,
                                         @Value("${spike.grid.width}") int gridWidth,
                                         @Value("${spike.grid.height}") int gridHeight,
                                         @Value("${spike.seed}") long seed,
                                         @Value("${spike.reseed}") boolean reseed) {
        repository.createSchema();

        // Seed PostgreSQL only when the stored graph is missing, a different size, or reseed is requested.
        int expectedNodes = gridWidth * gridHeight;
        if (reseed || repository.countNodes() != expectedNodes) {
            long start = System.nanoTime();
            repository.seed(GridGraphGenerator.generate(gridWidth, gridHeight, seed));
            System.out.printf("Seeded PostgreSQL with %d nodes in %.1f s%n",
                    expectedNodes, (System.nanoTime() - start) / 1e9);
        }

        RoutingService service = new RoutingService(repository);
        service.loadCache();
        return service;
    }
}
