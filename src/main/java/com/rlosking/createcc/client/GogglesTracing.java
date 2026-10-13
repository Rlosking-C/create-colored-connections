package com.rlosking.createcc.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.rlosking.createcc.ColoredConnections;
import com.rlosking.createcc.ConnectionColorManager;
import com.rlosking.createcc.ConnectionKey;
import com.rlosking.createcc.CreateCCConfig;
import com.rlosking.createcc.PanelGraph;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = ColoredConnections.MODID, value = Dist.CLIENT)
public final class GogglesTracing {

	private static final long FADE_IN_MS = 200;
	private static final long FADE_OUT_MS = 350;
	private static final long REFRESH_MS = 1000;

	private static final int COLOR_DONE = 0x9EFF7F;

	private static DyeColor tracedDye;
	private static Set<ConnectionKey> group = Set.of();
	private static Set<FactoryPanelPosition> network = Set.of();
	private static final List<OutgoingTrace> outgoing = new ArrayList<>();
	private static int gaugeCount;
	private static int idleCount, runningCount, doneCount, failedCount;
	private static ItemStack groupItem = ItemStack.EMPTY;
	private static int shortageCount;
	private static boolean networkWaiting;

	private static Level level;
	private static long lastRefreshMs;
	private static float strengthFrom, strengthTo;
	private static long stateChangeMs;

	private GogglesTracing() {}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		Level world = mc.level;
		if (player == null || world == null) {
			hardClear();
			return;
		}
		if (level != world)
			hardClear();
		level = world;

		long now = System.currentTimeMillis();
		boolean hoveringDyedLink = false;

		if (CreateCCConfig.GOGGLES_TRACING.get() && GogglesItem.isWearingGoggles(player)) {
			ConnectionHoverTracker.Key hovered = ConnectionHoverTracker.hoveredConnection();
			if (hovered != null && withinReach(mc)) {
				DyeColor dye = ConnectionColorManager.getColor(world, hovered.from(), hovered.to()).orElse(null);
				if (dye != null) {
					hoveringDyedLink = true;
					if (tracedDye == null || dye != tracedDye || !network.contains(hovered.to()) || strengthTo != 1)
						startTrace(world, dye, hovered.to(), now);
				}
			}
		}

		if (!hoveringDyedLink && strengthTo == 1)
			beginFade(now);

		if (strengthTo == 1 && tracedDye != null && now - lastRefreshMs >= REFRESH_MS && !group.isEmpty())
			refresh(world, now);

		if (strengthTo == 0 && strength(now) <= 0.001f)
			hardClear();

		if (!outgoing.isEmpty())
			outgoing.removeIf(trace -> trace.strength(now) <= 0.001f);
	}

	public static float grayStrengthFor(FactoryPanelPosition from, FactoryPanelPosition to) {
		if (tracedDye == null && outgoing.isEmpty())
			return 0;
		ConnectionKey key = new ConnectionKey(from, to);
		long now = System.currentTimeMillis();
		float gray = 0;
		for (OutgoingTrace trace : outgoing) {
			if (trace.group.contains(key) || !trace.network.contains(to))
				continue;
			gray = Math.max(gray, trace.strength(now));
		}
		if (tracedDye != null && !group.isEmpty() && !group.contains(key) && network.contains(to))
			gray = Math.max(gray, strength(now));
		return gray;
	}

	public static boolean shouldShowTooltip() {
		return tracedDye != null && !group.isEmpty()
			&& (strengthTo == 1 || strength(System.currentTimeMillis()) > 0.001f);
	}

	public static void appendTooltip(List<Component> lines) {
		String prefix = "gui." + ColoredConnections.MODID + ".";
		if (!lines.isEmpty())
			lines.add(CommonComponents.EMPTY);
		lines.add(indented(4, Component.translatable(prefix + "trace_header")));

		Component colorName = Component.translatable("color.minecraft." + tracedDye.getName())
			.withStyle(style -> style.withColor(tracedDye.getTextureDiffuseColor() & 0xFFFFFF));
		lines.add(indented(8, Component.translatable(prefix + "trace_summary",
			group.size(), colorName, gaugeCount)));

		lines.add(indented(4, Component.translatable(prefix + "trace_of_which")));

		List<Component> parts = new ArrayList<>();
		if (failedCount > 0)
			parts.add(statusPart("trace_failed", failedCount, ChatFormatting.RED));
		if (runningCount > 0)
			parts.add(statusPart("trace_running", runningCount, null));
		if (idleCount > 0)
			parts.add(statusPart("trace_idle", idleCount, null));
		if (doneCount > 0)
			parts.add(statusPart("trace_done", doneCount, null));
		Component separator = Component.translatable(prefix + "trace_status_separator");
		MutableComponent status = Component.empty();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0)
				status.append(separator);
			status.append(parts.get(i));
		}
		lines.add(indented(8, status));

		if (shortageCount > 0)
			lines.add(indented(8, statusPart("trace_shortage", shortageCount, ChatFormatting.YELLOW)));

		if (networkWaiting)
			lines.add(indented(8, Component.translatable("create.factory_panel.some_links_unloaded")
				.withStyle(ChatFormatting.YELLOW)));
	}

	private static Component indented(int spaces, Component text) {
		return Component.literal(" ".repeat(spaces)).append(text.copy().withStyle(ChatFormatting.GRAY));
	}

	private static Component statusPart(String key, int value, ChatFormatting valueColor) {
		Component number = valueColor == null
			? Component.literal(String.valueOf(value))
			: Component.literal(String.valueOf(value)).withStyle(valueColor);
		return Component.translatable("gui." + ColoredConnections.MODID + "." + key, number);
	}

	private static void startTrace(Level world, DyeColor dye, FactoryPanelPosition anchor, long now) {
		boolean switching = tracedDye != null && !group.isEmpty()
			&& (dye != tracedDye || !network.contains(anchor));
		if (switching) {
			outgoing.add(new OutgoingTrace(group, network, strength(now), now));
			strengthFrom = 0;
		} else {
			strengthFrom = strength(now);
		}
		tracedDye = dye;
		strengthTo = 1;
		stateChangeMs = now;
		recompute(world, anchor, now);
	}

	private static void refresh(Level world, long now) {
		recompute(world, group.iterator().next().to(), now);
	}

	private static void recompute(Level world, FactoryPanelPosition anchor, long now) {
		Set<FactoryPanelPosition> newNetwork = PanelGraph.componentOf(world, anchor);
		Set<ConnectionKey> newGroup = new HashSet<>();
		for (ConnectionKey key : ConnectionColorManager.clientKeysOfColor(tracedDye))
			if (newNetwork.contains(key.to()))
				newGroup.add(key);

		network = newNetwork;
		group = newGroup;
		lastRefreshMs = now;
		if (newGroup.isEmpty()) {
			beginFade(now);
			return;
		}
		computeStats(world);
	}

	private static void computeStats(Level world) {
		int idle = 0, running = 0, done = 0, failed = 0;
		Set<FactoryPanelPosition> panels = new HashSet<>();
		Set<FactoryPanelPosition> targets = new HashSet<>();
		for (ConnectionKey key : group) {
			panels.add(key.from());
			panels.add(key.to());
			targets.add(key.to());
			FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(world, key.to());
			if (behaviour == null || !behaviour.isActive()) {
				idle++;
				continue;
			}
			FactoryPanelConnection connection = behaviour.targetedBy.get(key.from());
			float glow = behaviour.bulb.getValue(0);
			boolean flashing = !behaviour.redstonePowered && !behaviour.waitingForNetwork
				&& glow > 0 && !behaviour.satisfied;
			if (flashing && connection != null && !connection.success) {
				failed++;
				continue;
			}
			if (behaviour.getIngredientStatusColor() == COLOR_DONE) {
				done++;
				continue;
			}
			if (behaviour.getPromised() > 0) {
				running++;
				continue;
			}
			idle++;
		}

		ItemStack unified = ItemStack.EMPTY;
		boolean mixed = false;
		int shortage = 0;
		boolean waiting = false;
		for (FactoryPanelPosition pos : targets) {
			FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(world, pos);
			if (behaviour == null || !behaviour.isActive())
				continue;
			if (behaviour.waitingForNetwork)
				waiting = true;
			ItemStack filter = behaviour.getFilter();
			if (filter.isEmpty())
				continue;
			if (unified.isEmpty())
				unified = filter;
			else if (!ItemStack.isSameItemSameComponents(unified, filter))
				mixed = true;
			int target = behaviour.getAmount() * (behaviour.upTo ? 1 : filter.getMaxStackSize());
			shortage += Math.max(0, target - behaviour.getLevelInStorage() - behaviour.getPromised());
		}
		gaugeCount = panels.size();
		idleCount = idle;
		runningCount = running;
		doneCount = done;
		failedCount = failed;
		groupItem = mixed ? ItemStack.EMPTY : unified;
		shortageCount = groupItem.isEmpty() ? 0 : shortage;
		networkWaiting = waiting;
	}

	private static boolean withinReach(Minecraft mc) {
		HitResult pick = mc.hitResult;
		if (!(pick instanceof BlockHitResult hit) || mc.player == null)
			return false;
		return mc.player.getEyePosition().distanceTo(hit.getLocation())
			<= CreateCCConfig.TRACE_DISTANCE.get();
	}

	private static void beginFade(long now) {
		if (strengthTo == 0)
			return;
		strengthFrom = strength(now);
		strengthTo = 0;
		stateChangeMs = now;
	}

	private static float strength(long now) {
		if (strengthTo == strengthFrom)
			return strengthTo;
		long duration = strengthTo > strengthFrom ? FADE_IN_MS : FADE_OUT_MS;
		float t = Math.min(1f, (now - stateChangeMs) / (float) duration);
		return strengthFrom + (strengthTo - strengthFrom) * t;
	}

	private static void hardClear() {
		tracedDye = null;
		group = Set.of();
		network = Set.of();
		outgoing.clear();
		gaugeCount = 0;
		idleCount = runningCount = doneCount = failedCount = 0;
		groupItem = ItemStack.EMPTY;
		shortageCount = 0;
		networkWaiting = false;
		strengthFrom = strengthTo = 0;
	}

	private static final class OutgoingTrace {
		private final Set<ConnectionKey> group;
		private final Set<FactoryPanelPosition> network;
		private final float fromStrength;
		private final long startMs;

		private OutgoingTrace(Set<ConnectionKey> group, Set<FactoryPanelPosition> network,
			float fromStrength, long startMs) {
			this.group = group;
			this.network = network;
			this.fromStrength = fromStrength;
			this.startMs = startMs;
		}

		private float strength(long now) {
			float t = Math.min(1f, (now - startMs) / (float) FADE_OUT_MS);
			return fromStrength * (1 - t);
		}
	}
}
