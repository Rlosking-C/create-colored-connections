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

public final class PanelGraph {

	private static final int MAX_VISITED = 2048;

	private static final int SCAN_REACH = 32;

	private PanelGraph() {}

	public static List<ConnectionKey> pathBetween(Level level, FactoryPanelPosition from, FactoryPanelPosition to) {
		return pathBetween(level, from, to, null);
	}

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

	public static List<ConnectionKey> pathBetween(Level level, FactoryPanelPosition from, FactoryPanelPosition to,
			Map<FactoryPanelPosition, Set<ConnectionKey>> downstreamCache) {
		if (from.equals(to))
			return List.of();

		Set<FactoryPanelPosition> visited = new HashSet<>();
		Map<FactoryPanelPosition, ConnectionKey> edgeTo = new HashMap<>();
		Queue<FactoryPanelPosition> queue = new ArrayDeque<>();

		visited.add(from);
		queue.add(from);

		while (!queue.isEmpty() && visited.size() < MAX_VISITED) {
			FactoryPanelPosition node = queue.poll();
			FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(level, node);
			if (behaviour == null)
				continue;

			for (FactoryPanelPosition source : behaviour.targetedBy.keySet()) {
				if (visited.add(source)) {
					edgeTo.put(source, new ConnectionKey(source, node));
					if (source.equals(to))
						return backtrack(edgeTo, from, to);
					queue.add(source);
				}
			}
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
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null)
					continue;
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (!(be instanceof FactoryPanelBlockEntity fpbe))
						continue;
					for (FactoryPanelBehaviour behaviour : fpbe.panels.values()) {
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

	private static List<ConnectionKey> backtrack(Map<FactoryPanelPosition, ConnectionKey> edgeTo,
			FactoryPanelPosition start, FactoryPanelPosition end) {
		List<ConnectionKey> path = new ArrayList<>();
		FactoryPanelPosition node = end;
		while (!node.equals(start)) {
			ConnectionKey edge = edgeTo.get(node);
			path.add(edge);
			node = edge.from().equals(node) ? edge.to() : edge.from();
		}
		for (int i = 0, j = path.size() - 1; i < j; i++, j--) {
			ConnectionKey tmp = path.get(i);
			path.set(i, path.get(j));
			path.set(j, tmp);
		}
		return path;
	}
}
