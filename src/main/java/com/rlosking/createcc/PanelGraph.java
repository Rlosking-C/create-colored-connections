package com.rlosking.createcc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Breadth-first search over the factory gauge connection graph, used by
 * path dyeing ("shift+right-click two gauges, dye every link between").
 *
 * <p>The graph is undirected for our purposes: two panels are neighbours
 * whenever a recipe link connects them, regardless of direction.</p>
 *
 * <p><b>Why edges are collected from {@code targetedBy} only:</b> Create
 * records an edge X→P on both endpoints — P.targetedBy (keyed by X) and
 * X.targeting (contains P) — but when a link is created,
 * {@code addConnection} re-syncs only the TARGET panel's block entity. On
 * the client, {@code targeting} is therefore stale (typically empty) for
 * source panels whose own state never changed afterwards, which made every
 * search that had to walk downstream of a node fail with a false "no
 * connection path" — the very bug that also kept the green preview from
 * ever appearing. {@code targetedBy} is the one structure that is complete
 * on both sides, so incoming edges are read directly and outgoing edges
 * are discovered by scanning the surrounding chunks for panels whose
 * {@code targetedBy} names the current node. Callers that search repeatedly
 * (the live preview resamples at 10 Hz) pass a per-node cache so each node
 * is scanned at most once per selection.</p>
 *
 * <p>BFS hop count is the right metric here: the path with the fewest
 * intermediate gauges is the one a player would trace as "the direct
 * route" through their network. Ties between equally short paths are
 * broken arbitrarily — the preview shows the exact path that will be
 * dyed, so there is no ambiguity for the player.</p>
 */
public final class PanelGraph {

	/**
	 * BFS visit cap. Guards against pathological networks (or a future mod
	 * generating huge link graphs) turning a single click into a long
	 * stall. Real recipe networks are far below this.
	 */
	private static final int MAX_VISITED = 2048;

	/**
	 * Outgoing-edge scan reach (Chebyshev, in blocks): every link target of
	 * a panel lives within this distance, so scanning the chunks overlapping
	 * this box around a node finds all its downstream neighbours. Deliberately
	 * generous — Create's own link range is smaller, so the box is guaranteed
	 * to contain every valid edge even if that range ever grows.
	 */
	private static final int SCAN_REACH = 32;

	private PanelGraph() {}

	/**
	 * Finds the shortest connection path between two panels, without an edge
	 * cache (each expansion scans its surroundings once). Use for one-shot
	 * requests such as the server-side validation of a batch packet.
	 */
	public static List<ConnectionKey> pathBetween(Level level, FactoryPanelPosition from, FactoryPanelPosition to) {
		return pathBetween(level, from, to, null);
	}

	/**
	 * The connected component of {@code anchor}: every panel reachable through
	 * recipe links, color ignored — the "one factory" scope for goggles
	 * tracing. Runs on the client each second at most (trace refresh), so the
	 * same visit cap and never-sync-load chunk scan as the path search apply.
	 */
	public static Set<FactoryPanelPosition> componentOf(Level level, FactoryPanelPosition anchor) {
		Set<FactoryPanelPosition> visited = new HashSet<>();
		Queue<FactoryPanelPosition> queue = new ArrayDeque<>();
		visited.add(anchor);
		queue.add(anchor);

		while (!queue.isEmpty() && visited.size() < MAX_VISITED) {
			FactoryPanelPosition node = queue.poll();
			FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(level, node);
			if (behaviour == null)
				continue;

			for (FactoryPanelPosition source : behaviour.targetedBy.keySet())
				if (visited.add(source))
					queue.add(source);
			for (ConnectionKey edge : downstreamEdges(level, node, null))
				if (visited.add(edge.to()))
					queue.add(edge.to());
		}
		return visited;
	}

	/**
	 * Finds the shortest connection path between two panels.
	 *
	 * @param downstreamCache optional per-node cache of discovered outgoing
	 *        edges; pass the same map across repeated searches so each node
	 *        is scanned at most once
	 * @return the connection keys along a shortest path, in start-to-end
	 *         order; {@code null} when the two panels are not connected;
	 *         empty when {@code from} equals {@code to}
	 */
	public static List<ConnectionKey> pathBetween(Level level, FactoryPanelPosition from, FactoryPanelPosition to,
			Map<FactoryPanelPosition, Set<ConnectionKey>> downstreamCache) {
		if (from.equals(to))
			return List.of();

		Set<FactoryPanelPosition> visited = new HashSet<>();
		// edge used to reach a node; lets us backtrack the path after the BFS
		Map<FactoryPanelPosition, ConnectionKey> edgeTo = new HashMap<>();
		Queue<FactoryPanelPosition> queue = new ArrayDeque<>();

		visited.add(from);
		queue.add(from);

		while (!queue.isEmpty() && visited.size() < MAX_VISITED) {
			FactoryPanelPosition node = queue.poll();
			FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(level, node);
			if (behaviour == null)
				continue;

			// Incoming edges: source → node. targetedBy maps the source
			// panel to its connection object; the connection key is
			// (source, node), mirroring how the color table is keyed.
			for (FactoryPanelPosition source : behaviour.targetedBy.keySet()) {
				if (visited.add(source)) {
					edgeTo.put(source, new ConnectionKey(source, node));
					if (source.equals(to))
						return backtrack(edgeTo, from, to);
					queue.add(source);
				}
			}
			// Outgoing edges: node → target, discovered from the target
			// panels' targetedBy (see the class doc for why X.targeting is
			// not trusted here)
			for (ConnectionKey edge : downstreamEdges(level, node, downstreamCache)) {
				if (visited.add(edge.to())) {
					edgeTo.put(edge.to(), edge);
					if (edge.to().equals(to))
						return backtrack(edgeTo, from, to);
					queue.add(edge.to());
				}
			}
		}
		return null;
	}

	/**
	 * The node's outgoing edges (node → target), discovered by scanning the
	 * chunks around the node for panels whose {@code targetedBy} names it.
	 * The scan iterates each chunk's block-entity map — a handful of entries
	 * per chunk — instead of probing blocks one by one, so it stays cheap
	 * even though the box spans several chunks. Results are cached per node
	 * when a cache is supplied.
	 */
	private static Set<ConnectionKey> downstreamEdges(Level level, FactoryPanelPosition node,
			Map<FactoryPanelPosition, Set<ConnectionKey>> downstreamCache) {
		if (downstreamCache != null) {
			Set<ConnectionKey> cached = downstreamCache.get(node);
			if (cached != null)
				return cached;
		}
		Set<ConnectionKey> edges = new HashSet<>();
		BlockPos center = node.pos();
		for (int cx = (center.getX() - SCAN_REACH) >> 4; cx <= (center.getX() + SCAN_REACH) >> 4; cx++) {
			for (int cz = (center.getZ() - SCAN_REACH) >> 4; cz <= (center.getZ() + SCAN_REACH) >> 4; cz++) {
				// getChunkNow: never sync-loads; unloaded chunks simply
				// contribute no edges (their panels are not rendered either)
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null)
					continue;
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (!(be instanceof FactoryPanelBlockEntity fpbe))
						continue;
					for (FactoryPanelBehaviour behaviour : fpbe.panels.values()) {
						// This panel's targetedBy naming the node = an edge
						// node → this panel (node is the link's source).
						// Link lines live in the separate targetedByLinks
						// map and never enter the graph.
						if (behaviour.targetedBy.containsKey(node))
							edges.add(new ConnectionKey(node, behaviour.getPanelPosition()));
					}
				}
			}
		}
		if (downstreamCache != null)
			downstreamCache.put(node, edges);
		return edges;
	}

	/**
	 * Rebuilds the path start-to-end by following the recorded edges
	 * backwards from the target.
	 */
	private static List<ConnectionKey> backtrack(Map<FactoryPanelPosition, ConnectionKey> edgeTo,
			FactoryPanelPosition start, FactoryPanelPosition end) {
		List<ConnectionKey> path = new ArrayList<>();
		FactoryPanelPosition node = end;
		while (!node.equals(start)) {
			ConnectionKey edge = edgeTo.get(node);
			path.add(edge);
			// step to the edge's other end (edges are undirected in this search)
			node = edge.from().equals(node) ? edge.to() : edge.from();
		}
		// the walk collected edges end-to-start; flip to start-to-end
		for (int i = 0, j = path.size() - 1; i < j; i++, j--) {
			ConnectionKey tmp = path.get(i);
			path.set(i, path.get(j));
			path.set(j, tmp);
		}
		return path;
	}
}
