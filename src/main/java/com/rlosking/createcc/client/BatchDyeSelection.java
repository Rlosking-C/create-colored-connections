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
import net.minecraft.network.chat.Component;
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

@EventBusSubscriber(modid = ColoredConnections.MODID, value = Dist.CLIENT)
public final class BatchDyeSelection {

	private static final long TIMEOUT_MS = 60_000;

	private static final List<FactoryPanelPosition> pathNodes = new ArrayList<>();
	private static Level selectedLevel;
	private static long selectedAt;

	private static FactoryPanelPosition lastHovered;

	private static final Set<ConnectionKey> previewPath = new HashSet<>();

	private static String rejection;
	private static long rejectedAt;

	private static final long REJECTION_HOLD_MS = 1600;

	private static final Map<FactoryPanelPosition, Set<ConnectionKey>> downstreamCache = new HashMap<>();

	private static boolean endSyncQueued;

	private BatchDyeSelection() {}

	public static boolean hasSelection() {
		return !pathNodes.isEmpty();
	}

	public static List<FactoryPanelPosition> nodes() {
		return List.copyOf(pathNodes);
	}

	public static FactoryPanelPosition tail() {
		return pathNodes.isEmpty() ? null : pathNodes.get(pathNodes.size() - 1);
	}

	public static int size() {
		return pathNodes.size();
	}

	public static FactoryPanelPosition start() {
		return pathNodes.isEmpty() ? null : pathNodes.get(0);
	}

	public static int linkCount() {
		return previewPath.size();
	}

	public static String rejection() {
		return rejection;
	}

	public static boolean rejectionFresh() {
		return rejection != null && Util.getMillis() - rejectedAt < REJECTION_HOLD_MS;
	}

	public static Set<ConnectionKey> previewPath() {
		return previewPath;
	}

	public static void select(Level level, FactoryPanelPosition start) {
		pathNodes.clear();
		pathNodes.add(start);
		selectedLevel = level;
		selectedAt = Util.getMillis();
		downstreamCache.clear();
		previewPath.clear();
		rejection = null;
		lastHovered = start;
		endSyncQueued = false;
		PacketDistributor.sendToServer(new BatchSelectionModePacket(true));
	}

	public static void clear() {
		if (pathNodes.isEmpty())
			return;
		pathNodes.clear();
		selectedLevel = null;
		lastHovered = null;
		previewPath.clear();
		downstreamCache.clear();
		rejection = null;
		queueEndSync();
	}

	public static void step(Level level, FactoryPanelPosition pos) {
		step(level, pos, false);
	}

	public static void step(Level level, FactoryPanelPosition pos, boolean report) {
		if (pathNodes.isEmpty())
			return;
		lastHovered = pos;
		selectedAt = Util.getMillis();

		if (pos.equals(tail()) && pathNodes.size() > 1) {
			pathNodes.remove(pathNodes.size() - 1);
			rejection = null;
			rebuildPreview(level);
			return;
		}
		if (pathNodes.contains(pos) || onPreviewPath(pos)) {
			if (report)
				reject("message.create_colored_connections.path_already_in_chain");
			return;
		}
		if (PanelGraph.pathBetween(level, tail(), pos, downstreamCache) == null) {
			if (report)
				reject("message.create_colored_connections.path_no_route");
			return;
		}
		pathNodes.add(pos);
		rejection = null;
		rebuildPreview(level);
	}

	private static void reject(String key) {
		rejection = key;
		rejectedAt = Util.getMillis();
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (endSyncQueued && mc.player != null) {
			endSyncQueued = false;
			PacketDistributor.sendToServer(new BatchSelectionModePacket(false));
		}
		if (pathNodes.isEmpty())
			return;

		Level level = mc.level;
		if (level == null || level != selectedLevel) {
			announce(mc.player, "message.create_colored_connections.path_cancel");
			clear();
			return;
		}
		if (Util.getMillis() - selectedAt > TIMEOUT_MS) {
			announce(mc.player, "message.create_colored_connections.path_timeout");
			clear();
			return;
		}
		Player player = mc.player;
		if (player == null || !holdingDye(player)) {
			announce(player, "message.create_colored_connections.path_no_dye");
			clear();
			return;
		}

		FactoryPanelPosition hovered = hoveredGauge(mc, level);
		if (hovered == null) {
			lastHovered = null;
			return;
		}
		if (hovered.equals(lastHovered))
			return;
		step(level, hovered);
	}

	private static void rebuildPreview(Level level) {
		previewPath.clear();
		for (int i = 0; i + 1 < pathNodes.size(); i++) {
			List<ConnectionKey> segment =
				PanelGraph.pathBetween(level, pathNodes.get(i), pathNodes.get(i + 1), downstreamCache);
			if (segment != null)
				previewPath.addAll(segment);
		}
	}

	private static boolean onPreviewPath(FactoryPanelPosition pos) {
		for (ConnectionKey key : previewPath)
			if (key.from().equals(pos) || key.to().equals(pos))
				return true;
		return false;
	}

	private static FactoryPanelPosition hoveredGauge(Minecraft mc, Level level) {
		HitResult pick = mc.hitResult;
		if (!(pick instanceof BlockHitResult blockHit))
			return null;
		BlockState state = level.getBlockState(blockHit.getBlockPos());
		if (!(state.getBlock() instanceof FactoryPanelBlock))
			return null;
		FactoryPanelPosition hovered = new FactoryPanelPosition(blockHit.getBlockPos(),
			FactoryPanelBlock.getTargetedSlot(blockHit.getBlockPos(), state, blockHit.getLocation()));
		FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(level, hovered);
		return behaviour != null && behaviour.isActive() ? hovered : null;
	}

	private static void queueEndSync() {
		endSyncQueued = true;
	}

	private static void announce(Player player, String key) {
		if (player != null)
			player.displayClientMessage(Component.translatable(key), true);
	}

	private static boolean holdingDye(Player player) {
		return player.getMainHandItem().getItem() instanceof DyeItem
			|| player.getOffhandItem().getItem() instanceof DyeItem;
	}
}
