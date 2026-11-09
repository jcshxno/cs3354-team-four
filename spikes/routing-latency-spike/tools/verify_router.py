"""
Checks the Java router (queries.csv) against networkx on the same graph (edges.csv).
For every query: (1) the distance matches networkx, (2) the returned path is a real
chain of edges, (3) the path's summed edge cost equals the reported distance.
Usage: python3 tools/verify_router.py <dir containing edges.csv and queries.csv>
"""
import csv
import math
import sys
from pathlib import Path

import networkx as nx

TOLERANCE = 1e-6


def main():
    folder = Path(sys.argv[1])
    graph = nx.Graph()
    with open(folder / "edges.csv", newline="") as handle:
        for row in csv.DictReader(handle):
            graph.add_edge(int(row["source"]), int(row["target"]), weight=float(row["cost"]))

    checked = 0
    failures = []
    with open(folder / "queries.csv", newline="") as handle:
        for row in csv.DictReader(handle):
            start, end = int(row["from"]), int(row["to"])
            found = row["found"] == "true"
            try:
                expected = nx.dijkstra_path_length(graph, start, end, weight="weight")
            except nx.NetworkXNoPath:
                expected = None
            if (expected is None) != (not found):
                failures.append(f"{start}->{end}: found={found} but networkx expected={expected}")
                continue
            if expected is not None:
                distance = float(row["distance"])
                path = [int(n) for n in row["path"].split()]
                summed = sum(graph[a][b]["weight"] for a, b in zip(path, path[1:]))
                if not math.isclose(distance, expected, rel_tol=TOLERANCE):
                    failures.append(f"{start}->{end}: distance {distance} != networkx {expected}")
                elif path[0] != start or path[-1] != end:
                    failures.append(f"{start}->{end}: path endpoints wrong")
                elif not math.isclose(summed, distance, rel_tol=TOLERANCE):
                    failures.append(f"{start}->{end}: path sums to {summed}, reported {distance}")
            checked += 1

    print(f"Checked {checked} queries against networkx {nx.__version__}")
    if failures:
        print(f"FAILED: {len(failures)} mismatches")
        for line in failures[:10]:
            print("  ", line)
        sys.exit(1)
    print("PASS: every distance matches and every path is valid")


if __name__ == "__main__":
    main()
