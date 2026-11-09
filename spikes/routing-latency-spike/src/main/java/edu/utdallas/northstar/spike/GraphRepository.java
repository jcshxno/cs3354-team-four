/*
Module Name: GraphRepository
Date Written: 2026-11-08
Author: Joshua Ayeh-Kwapong
Module Purpose: All PostgreSQL access for the spike (plain JDBC). Creates the
    schema, bulk-loads a generated graph, and reads the graph back.
*/
package edu.utdallas.northstar.spike;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class GraphRepository implements EdgeListSource {
    private static final String SCHEMA_RESOURCE = "/schema.sql";
    private static final String COMMENT_PREFIX = "--";
    private static final int BATCH_SIZE = 5_000;
    private static final int FETCH_SIZE = 10_000;

    private final DataSource dataSource;

    public GraphRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void createSchema() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : readSchemaStatements()) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create schema", e);
        }
    }

    public int countNodes() {
        try (Connection connection = dataSource.getConnection()) {
            return count(connection, "node");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not count nodes", e);
        }
    }

    /** Replaces whatever is stored with the given graph. */
    public void seed(EdgeList graph) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("TRUNCATE TABLE edge, node");
                }
                insertNodes(connection, graph);
                insertEdges(connection, graph);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not seed graph", e);
        }
    }

    /** Reads the whole graph. A fetch size makes the driver stream rows instead of buffering them all. */
    @Override
    public EdgeList loadEdgeList() {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);   // PostgreSQL only streams with autocommit off
            try {
                int nodeCount = count(connection, "node");
                int edgeCount = count(connection, "edge");
                double[] x = new double[nodeCount];
                double[] y = new double[nodeCount];
                int[] source = new int[edgeCount];
                int[] target = new int[edgeCount];
                double[] cost = new double[edgeCount];

                try (PreparedStatement statement = connection.prepareStatement("SELECT id, x, y FROM node")) {
                    statement.setFetchSize(FETCH_SIZE);
                    try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) {
                            int id = rows.getInt(1);
                            if (id < 0 || id >= nodeCount) {
                                throw new IllegalStateException("Node ids must be 0.." + (nodeCount - 1) + " but found " + id);
                            }
                            x[id] = rows.getDouble(2);
                            y[id] = rows.getDouble(3);
                        }
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("SELECT source, target, cost FROM edge")) {
                    statement.setFetchSize(FETCH_SIZE);
                    try (ResultSet rows = statement.executeQuery()) {
                        int i = 0;
                        while (rows.next() && i < edgeCount) {
                            source[i] = rows.getInt(1);
                            target[i] = rows.getInt(2);
                            cost[i] = rows.getDouble(3);
                            i++;
                        }
                    }
                }
                return new EdgeList(nodeCount, x, y, source, target, cost);
            } finally {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load graph", e);
        }
    }

    private static int count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static void insertNodes(Connection connection, EdgeList graph) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO node (id, x, y) VALUES (?, ?, ?)")) {
            for (int id = 0; id < graph.nodeCount(); id++) {
                statement.setInt(1, id);
                statement.setDouble(2, graph.x()[id]);
                statement.setDouble(3, graph.y()[id]);
                statement.addBatch();
                if ((id + 1) % BATCH_SIZE == 0) {
                    statement.executeBatch();
                }
            }
            statement.executeBatch();
        }
    }

    private static void insertEdges(Connection connection, EdgeList graph) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO edge (id, source, target, cost) VALUES (?, ?, ?, ?)")) {
            for (int i = 0; i < graph.edgeCount(); i++) {
                statement.setInt(1, i);
                statement.setInt(2, graph.source()[i]);
                statement.setInt(3, graph.target()[i]);
                statement.setDouble(4, graph.cost()[i]);
                statement.addBatch();
                if ((i + 1) % BATCH_SIZE == 0) {
                    statement.executeBatch();
                }
            }
            statement.executeBatch();
        }
    }

    private static String[] readSchemaStatements() {
        try (InputStream stream = GraphRepository.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing " + SCHEMA_RESOURCE + " on the classpath");
            }
            StringBuilder withoutComments = new StringBuilder();
            for (String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.trim().startsWith(COMMENT_PREFIX)) {
                    withoutComments.append(line).append('\n');
                }
            }
            return withoutComments.toString().split(";\\s*\n?");
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + SCHEMA_RESOURCE, e);
        }
    }
}
