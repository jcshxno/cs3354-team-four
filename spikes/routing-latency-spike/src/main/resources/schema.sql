-- Walkway graph for the routing spike. Undirected edges are stored once.
-- Costs are walking distance in meters.
CREATE TABLE IF NOT EXISTS node (
    id INTEGER PRIMARY KEY,
    x  DOUBLE PRECISION NOT NULL,
    y  DOUBLE PRECISION NOT NULL
);
CREATE TABLE IF NOT EXISTS edge (
    id     INTEGER PRIMARY KEY,
    source INTEGER NOT NULL REFERENCES node (id),
    target INTEGER NOT NULL REFERENCES node (id),
    cost   DOUBLE PRECISION NOT NULL CHECK (cost >= 0)
);
CREATE INDEX IF NOT EXISTS edge_source_idx ON edge (source);
CREATE INDEX IF NOT EXISTS edge_target_idx ON edge (target);
