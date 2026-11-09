# Routing Latency Spike (P2) — North Star Navigation

## The question
**Can Spring Boot + PostgreSQL return a campus route within 3 seconds?** (QR-01; also exercises FR-03 and QR-03.)
This is our riskiest design idea: if route generation is too slow, the architecture in P2 has to change.

## What this spike is
A throwaway Spring Boot service (`GET /route?from=&to=&engine=`) backed by PostgreSQL, plus benchmarks. It compares two designs:

| Engine | Design |
|---|---|
| `cached` | Read the graph from PostgreSQL once at startup, keep it in memory, run Dijkstra per request |
| `per_request` | Re-read the whole graph from PostgreSQL on every request (the naive design), then run Dijkstra |

**Data is synthetic.** Real UTD path data is not available yet (assumption A1), so a street-grid graph with blocked edges stands in. This measures *architecture latency*, not route quality or QR-02 accuracy.
Sizes: 10,000 nodes (our estimate for campus + building interiors), 100,000 (10x margin), 500,000 (50x margin).

## How the router was checked
`tools/verify_router.py` compares our Dijkstra with networkx on the same graph and validates each returned path (1,200 queries over 3 graphs: all matched).

## Run it (about 15 minutes; needs JDK 17+, Maven, Docker)
```bash
docker compose up -d                     # PostgreSQL 16
mvn -q compile
java -cp target/classes bench/RoutingCoreBench.java      # routing core only, ~1 min

# Terminal 1: start the service (10,000 nodes). Wait for "Graph cached in memory".
mvn spring-boot:run
# Terminal 2: end-to-end benchmark over real HTTP
java bench/Benchmark.java
```
Repeat at larger sizes (stop the service, restart with a bigger grid, rerun the benchmark):
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spike.grid.width=316 --spike.grid.height=316"
java bench/Benchmark.java
mvn spring-boot:run -Dspring-boot.run.arguments="--spike.grid.width=707 --spike.grid.height=707"
java bench/Benchmark.java --requests 20 --warmup 3       # per_request is slow here
```
Each run appends to `results/e2e.md` and refreshes the tables below. `bench/StubServer.java` is only a test double for the benchmark client; its numbers are not results.

## Results
### Routing core (no HTTP, no database)
<!-- CORE-RESULTS:START -->
**Routing core only (in memory; no HTTP, no PostgreSQL).** 2026-11-08T20:50 | Java 21.0.13 | Windows 11 amd64 | 8 CPU(s) | max heap 3996 MB

200 timed searches per row after 30 warm-up; "far" = opposite corners of the map (worst case for Dijkstra). Synthetic grid graph, seed 42.

| Nodes | Edges | Build graph (ms) | Pair mode | p50 (ms) | p95 (ms) | max (ms) | Avg nodes searched | Max as % of 3 s |
|---:|---:|---:|---|---:|---:|---:|---:|---:|
| 1,024 | 1,811 | 0.4 | random | 0.10 | 0.33 | 2.19 | 531 | 0.07% |
| 1,024 | 1,811 | 0.4 | far | 0.14 | 0.37 | 1.89 | 1,019 | 0.06% |
| 10,000 | 17,817 | 3.5 | random | 0.91 | 2.09 | 7.29 | 4,911 | 0.24% |
| 10,000 | 17,817 | 3.5 | far | 3.12 | 4.16 | 7.11 | 9,970 | 0.24% |
| 99,856 | 179,354 | 90.2 | random | 14.98 | 46.81 | 166.69 | 53,904 | 5.56% |
| 99,856 | 179,354 | 90.2 | far | 22.61 | 49.16 | 67.24 | 99,588 | 2.24% |
| 499,849 | 898,774 | 157.7 | random | 79.73 | 192.05 | 283.32 | 239,747 | 9.44% |
| 499,849 | 898,774 | 157.7 | far | 186.56 | 289.99 | 324.04 | 498,519 | 10.80% |

Slowest single search in this run: 324.04 ms (10.80% of the 3000 ms budget).

<!-- CORE-RESULTS:END -->

### End to end (Spring Boot + PostgreSQL, real HTTP)
<!-- E2E-RESULTS:START -->
_Not run yet. Run `java bench/Benchmark.java` against the running service (see above)._
<!-- E2E-RESULTS:END -->

## Decision rule (set before measuring)
- `cached` meets QR-01 at 100,000 nodes: **adopt load-once, in-memory routing** backed by PostgreSQL.
- `per_request` also meets it: the simple design is acceptable; caching is optional.
- `cached` fails: switch to A* or move routing into the database (pgRouting) and re-test.

**Decision:** _(fill in after the end-to-end run)_

## Limits
Synthetic data; one machine (client, service and database together, so no network delay); low concurrency by default; no indoor floors, no route preferences (FR-06/07).
