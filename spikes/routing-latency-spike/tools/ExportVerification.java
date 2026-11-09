/*
Module Name: ExportVerification
Date Written: 2026-11-08
Author: Joshua Ayeh-Kwapong
Module Purpose: Writes a generated graph (edges.csv) and this project's route
    answers (queries.csv) so tools/verify_router.py can check them against an
    independent shortest-path library.
Run: java -cp target/classes tools/ExportVerification.java <width> <height> <seed> <queries> <outDir>
*/
import edu.utdallas.northstar.spike.DijkstraRouter;
import edu.utdallas.northstar.spike.EdgeList;
import edu.utdallas.northstar.spike.Graph;
import edu.utdallas.northstar.spike.GridGraphGenerator;
import edu.utdallas.northstar.spike.PairSampler;
import edu.utdallas.northstar.spike.RouteResult;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public class ExportVerification {
    public static void main(String[] args) throws Exception {
        int width = Integer.parseInt(args[0]);
        int height = Integer.parseInt(args[1]);
        long seed = Long.parseLong(args[2]);
        int queries = Integer.parseInt(args[3]);
        Path outDir = Path.of(args[4]);
        Files.createDirectories(outDir);

        EdgeList edges = GridGraphGenerator.generate(width, height, seed);
        Graph graph = Graph.from(edges);

        try (PrintWriter out = new PrintWriter(outDir.resolve("edges.csv").toFile())) {
            out.println("source,target,cost");
            for (int i = 0; i < edges.edgeCount(); i++) {
                out.println(edges.source()[i] + "," + edges.target()[i] + "," + edges.cost()[i]);
            }
        }
        try (PrintWriter out = new PrintWriter(outDir.resolve("queries.csv").toFile())) {
            out.println("from,to,found,distance,path");
            for (String mode : new String[]{PairSampler.MODE_RANDOM, PairSampler.MODE_FAR}) {
                for (int[] pair : PairSampler.sample(graph, queries, seed + 1, mode)) {
                    RouteResult result = DijkstraRouter.route(graph, pair[0], pair[1]);
                    StringBuilder path = new StringBuilder();
                    for (int node : result.path()) {
                        path.append(node).append(' ');
                    }
                    out.println(pair[0] + "," + pair[1] + "," + result.found() + ","
                            + result.distanceMeters() + "," + path.toString().trim());
                }
            }
        }
        System.out.println("Wrote " + edges.edgeCount() + " edges and " + (2 * queries) + " queries to " + outDir);
    }
}
