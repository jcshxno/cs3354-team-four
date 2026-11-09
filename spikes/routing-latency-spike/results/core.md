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
