package com.rlosking.createcc.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.rlosking.createcc.ColoredConnections;
import com.rlosking.createcc.ConnectionKey;
import com.rlosking.createcc.PanelGraph;
import com.rlosking.createcc.network.BatchSelectionModePacket;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-side state machine for path dyeing: the player sweep-builds a
 * chain of gauges and every link along it gets dyed in one action.
 *
 * <p>Flow: shift+right-click on a gauge starts the chain; sweeping the
 * crosshair over further gauges appends them one by one (each new gauge
 * is connected to the current chain end by the shortest route, so
 * sweeping A-B-C then the branch D-F and finally G builds A-B-C-D-F-G
 * even where a shorter C-G shortcut exists — the sweep, not the graph,
 * picks the route); visiting the chain's tail gauge a second time drops
 * it again (undo); a plain right-click on the tail confirms and sends
 * the batch packet, a plain right-click anywhere else cancels. While the
 * chain is pending every one of its links carries a floating green
 * preview beam (the beam renderer queries {@link #previewPath()}).</p>
 *
 * <p>Selection state is purely client-held — nothing is synced except
 * the single "mode active" boolean the server needs to suppress vanilla
 * gauge clicks (see {@link BatchSelectionModePacket}); the node chain
 * itself travels only inside the confirming packet and the server
 * re-derives every segment from its own state.</p>
 *
 * <p>Lifecycle guards: the selection silently expires when the player
 * switches away from dyes, changes dimension/world, or stays idle for
 * a minute — a pending selection can never linger as invisible state.</p>
 */
@EventBusSubscriber(modid = ColoredConnections.MODID, value = Dist.CLIENT)
public final class BatchDyeSelection {

	/** A pending selection expires after this much inactivity, so stale state never lingers invisibly */
	private static final long TIMEOUT_MS = 60_000;

	/** The swept gauges, in sweep order — the chain the confirming packet will dye */
	private static final List<FactoryPanelPosition> pathNodes = new ArrayList<>();
	private static Level selectedLevel;
	private static long selectedAt;

	/**
	 * The gauge the crosshair sat on last sample. Append/undo decisions are
	 * made on hover TRANSITIONS only: acting on every tick would toggle a
	 * gauge on and off at 20 Hz while the crosshair rests on it.
	 */
	private static FactoryPanelPosition lastHovered;

	/** Connection keys of the currently previewed chain; the beam renderer walks this every client tick */
	private static final Set<ConnectionKey> previewPath = new HashSet<>();

	/**
	 * Per-node cache of outgoing graph edges for the preview's repeated path
	 * searches (every chain change re-derives all segments; without the cache
	 * each re-derivation would rescan the chunks around every path node
	 * again). Lives as long as the selection.
	 */
	private static final Map<FactoryPanelPosition, Set<ConnectionKey>> downstreamCache = new HashMap<>();

	/**
	 * Queued "selection ended" sync — sent on the NEXT client tick, one tick
	 * after the selection actually ended. Ending and confirming happen inside
	 * the same click, and the click's use-item-on packet leaves the client
	 * after our packets but before the next tick; an immediate sync would
	 * therefore land first and the server would no longer suppress the
	 * vanilla gauge click it was queued to suppress.
	 */
	private static boolean endSyncQueued;

	private BatchDyeSelection() {}

	/** Whether a chain is currently being built. */
	public static boolean hasSelection() {
		return !pathNodes.isEmpty();
	}

	/** The swept gauges in sweep order (a copy — callers cannot mutate the chain). */
	public static List<FactoryPanelPosition> nodes() {
		return List.copyOf(pathNodes);
	}

	/** The chain's last gauge — the confirm target — or null when no selection is pending. */
	public static FactoryPanelPosition tail() {
		return pathNodes.isEmpty() ? null : pathNodes.get(pathNodes.size() - 1);
	}

	/** Number of gauges in the chain. */
	public static int size() {
		return pathNodes.size();
	}

	/** The connections of the currently previewed chain — the beam renderer walks this every client tick. */
	public static Set<ConnectionKey> previewPath() {
		return previewPath;
	}

	/**
	 * Starts a new chain at the given gauge (called by the interaction
	 * handler on shift+right-click). The server is told immediately: the
	 * starting click is already cancelled on both sides by the stateless
	 * shift-branch, so there is no packet-order race to win here.
	 */
	public static void select(Level level, FactoryPanelPosition start) {
		pathNodes.clear();
		pathNodes.add(start);
		selectedLevel = level;
		selectedAt = Util.getMillis();
		downstreamCache.clear();
		previewPath.clear();
		// the crosshair sits on the start gauge right after the click; seeding
		// the hover memory keeps the first sample from re-processing it
		lastHovered = start;
		endSyncQueued = false;
		PacketDistributor.sendToServer(new BatchSelectionModePacket(true));
	}

	/** Drops the pending chain (and with it the preview). */
	public static void clear() {
		if (pathNodes.isEmpty())
			return;
		pathNodes.clear();
		selectedLevel = null;
		lastHovered = null;
		previewPath.clear();
		downstreamCache.clear();
		queueEndSync();
	}

	/**
	 * One sweep step onto a gauge: a fresh gauge is appended (connected by
	 * the shortest route from the current tail), while re-visiting the tail
	 * drops it again. Called on hover transitions from the tick sampler and
	 * on shift+right-click from the interaction handler (a click-based
	 * fallback for when the crosshair never rested on a gauge long enough
	 * to register).
	 */
	public static void step(Level level, FactoryPanelPosition pos) {
		if (pathNodes.isEmpty())
			return;
		// remember the visit either way: while the crosshair rests on this
		// gauge no further transitions fire, so an appended gauge cannot be
		// immediately un-appended (and vice versa) by the sampler
		lastHovered = pos;
		// any activity keeps the selection alive
		selectedAt = Util.getMillis();

		// re-visiting the tail undoes it (the user's escape hatch for a
		// mis-swept branch); the lone start node is exempt so sweeping back
		// over the start cannot silently kill the whole selection
		if (pos.equals(tail()) && pathNodes.size() > 1) {
			pathNodes.remove(pathNodes.size() - 1);
			rebuildPreview(level);
			return;
		}
		// gauges already in the chain — or lying on an already-built segment
		// of it, like the intermediates of a shortest route — are no-ops:
		// re-sweeping part of the chain neither reorders nor loops it
		if (pathNodes.contains(pos) || onPreviewPath(pos))
			return;
		// a gauge of a different (unconnected) network never joins the chain;
		// like the old two-gauge preview, showing nothing already says "no path"
		if (PanelGraph.pathBetween(level, tail(), pos, downstreamCache) == null)
			return;
		pathNodes.add(pos);
		rebuildPreview(level);
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		// flush the deferred "selection ended" sync (see endSyncQueued)
		if (endSyncQueued && mc.player != null) {
			endSyncQueued = false;
			PacketDistributor.sendToServer(new BatchSelectionModePacket(false));
		}
		if (pathNodes.isEmpty())
			return;

		Level level = mc.level;
		// World left / dimension switched / expired: drop the selection
		if (level == null || level != selectedLevel || Util.getMillis() - selectedAt > TIMEOUT_MS) {
			clear();
			return;
		}
		// Switching away from dyes ends the mode — the selection is only
		// meaningful with a dye in hand
		Player player = mc.player;
		if (player == null || !holdingDye(player)) {
			clear();
			return;
		}

		FactoryPanelPosition hovered = hoveredGauge(mc, level);
		if (hovered == null) {
			// Leaving every gauge resets the transition memory, so coming
			// back to the tail later counts as a fresh visit (= undo)
			lastHovered = null;
			return;
		}
		if (hovered.equals(lastHovered))
			return;
		step(level, hovered);
	}

	/**
	 * Rebuilds the preview from scratch: every consecutive pair of chain
	 * nodes contributes its shortest route. Rebuilding wholesale (instead
	 * of appending one segment) keeps undo trivially correct — dropping
	 * the tail simply drops its segment. With the per-node edge cache this
	 * is a few cache lookups per segment.
	 */
	private static void rebuildPreview(Level level) {
		previewPath.clear();
		for (int i = 0; i + 1 < pathNodes.size(); i++) {
			List<ConnectionKey> segment =
				PanelGraph.pathBetween(level, pathNodes.get(i), pathNodes.get(i + 1), downstreamCache);
			if (segment != null)
				previewPath.addAll(segment);
		}
	}

	/** Whether the gauge lies on the already-built preview (a chain node or a shortest-route intermediate). */
	private static boolean onPreviewPath(FactoryPanelPosition pos) {
		for (ConnectionKey key : previewPath)
			if (key.from().equals(pos) || key.to().equals(pos))
				return true;
		return false;
	}

	/** The active gauge panel under the crosshair, or null when it is on anything else. */
	private static FactoryPanelPosition hoveredGauge(Minecraft mc, Level level) {
		HitResult pick = mc.hitResult;
		if (!(pick instanceof BlockHitResult blockHit))
			return null;
		BlockState state = level.getBlockState(blockHit.getBlockPos());
		if (!(state.getBlock() instanceof FactoryPanelBlock))
			return null;
		// Which of the block's four panel slots the crosshair is on
		FactoryPanelPosition hovered = new FactoryPanelPosition(blockHit.getBlockPos(),
			FactoryPanelBlock.getTargetedSlot(blockHit.getBlockPos(), state, blockHit.getLocation()));
		FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(level, hovered);
		return behaviour != null && behaviour.isActive() ? hovered : null;
	}

	private static void queueEndSync() {
		endSyncQueued = true;
	}

	private static boolean holdingDye(Player player) {
		return player.getMainHandItem().getItem() instanceof DyeItem
			|| player.getOffhandItem().getItem() instanceof DyeItem;
	}
}
