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

/**
 * Goggles tracing: while wearing Create's engineer goggles, hovering a dyed
 * link keeps that link's whole <b>color group</b> fully lit while every other
 * connection line in the same factory dims to gray — the "read your network"
 * tool for dense factories.
 *
 * <p><b>Trace group scope (author decision):</b> the group is the hovered
 * link's dye color <i>within its connected factory network</i> (the connected
 * component of panels joined by recipe links, color ignored). Same-colored
 * links in a different factory are neither highlighted nor dimmed — players
 * look at one factory at a time, so other factories render completely
 * vanilla. HUD numbers and highlighted lines always come from the same set:
 * what lights up is what is counted.</p>
 *
 * <p><b>The trace follows the crosshair (author decision):</b> as long as the
 * crosshair rests on a dyed link the trace stays; the moment it leaves, the
 * highlight fades out smoothly. Hovering a dyed link of another color (or
 * the same color in another factory) <i>cross-fades</i>: the replaced group
 * keeps dimming out on the fade-out curve while the new group lights up on
 * the fade-in curve — two overlapping ordinary transitions, never a hard
 * swap. Sweeping the crosshair from factory A to factory B therefore
 * re-traces B smoothly even when both use the same dye.</p>
 *
 * <p><b>HUD (author decision):</b> no custom HUD — the trace summary and
 * status lines are appended straight into Create's own goggle overlay
 * tooltip, so they reuse its position (overlay offset config), styling, item
 * icon and slide/fade-in animation. Injected by
 * {@code GoggleOverlayRendererMixin}. Like every Create goggle line, ours
 * carry a 4-space (16px) leading indent so the overlay's corner item icon
 * never covers the text.</p>
 *
 * <p><b>HUD contents (author decision):</b> a two-level list —
 * "In this production line:" with the traced color, link and gauge counts
 * indented under it, then "Of which:" with the status counters indented
 * under that. No item name: groups rarely share one filter item, and naming
 * the group by the item its links converge to is deferred to a later
 * version. The status line shows only non-zero counters, most actionable
 * first: failed (red), running, idle, met. Below it, the group's shortage
 * (yellow) — its shopping list, Σ(target − in storage − in transit) over the
 * group's distinct target gauges, replicating the game's own target formula
 * ({@code getAmount() × (upTo ? 1 : max stack size)}; stack mode counts full
 * stacks) — appears only when every target gauge filters the same item,
 * because a bare count over mixed items would be meaningless. Last, a
 * yellow warning line reusing Create's own "some links are not loaded"
 * wording appears when any target gauge has stock links sitting in unloaded
 * chunks (that is also what the dark-gray resting lines mean).</p>
 *
 * <p><b>Status classification (author decision):</b> "running" means items
 * are physically in transit toward the gauge right now — the link's target
 * panel has outstanding logistics promises ({@code getPromised() > 0},
 * synced to clients as {@code lastReportedPromises}). A panel merely holding
 * an unfulfilled request (blue resting line, nothing moving) is <i>not</i>
 * running — it is idle, exactly like the gray resting states (no amount
 * configured / missing address / redstone powered / waiting for network).
 * Green resting lines ({@code satisfied}) are done, and the red restock
 * flash (bulb glowing while unsatisfied and the shipment failed) is
 * failed.</p>
 *
 * <p><b>Multiplayer:</b> per-player by design (scheme A) — the trace state
 * lives entirely in this client, nothing is synced or broadcast; two players
 * can trace different colors of the same network without affecting each
 * other.</p>
 *
 * <p>Client-only. The hover target comes from
 * {@link ConnectionHoverTracker} (shared sticky picking), the color table from
 * the client mirror in {@link ConnectionColorManager}, the component via
 * {@link PanelGraph#componentOf}.</p>
 */
@EventBusSubscriber(modid = ColoredConnections.MODID, value = Dist.CLIENT)
public final class GogglesTracing {

	/** Highlight fade-in after a trace starts (ms) — a quick ramp, not a blink */
	private static final long FADE_IN_MS = 200;
	/** Highlight fade-out after the crosshair leaves the dyed links (ms) */
	private static final long FADE_OUT_MS = 350;
	/** Group + statistics refresh interval while a trace is active (ms): picks up new links, recolors and status changes */
	private static final long REFRESH_MS = 1000;

	/** {@code getIngredientStatusColor} value for a satisfied recipe: green line = done */
	private static final int COLOR_DONE = 0x9EFF7F;

	/** The dye color currently traced; null when idle */
	private static DyeColor tracedDye;
	/** The traced color group: same-color links inside the hovered link's connected network */
	private static Set<ConnectionKey> group = Set.of();
	/** The traced network (connected component of panels): the highlight boundary — links outside render vanilla */
	private static Set<FactoryPanelPosition> network = Set.of();
	/** Traces replaced by a newer one, still dimming out: a color or network switch cross-fades instead of popping */
	private static final List<OutgoingTrace> outgoing = new ArrayList<>();
	/** Distinct panels touched by the traced group (for the HUD gauge count) */
	private static int gaugeCount;
	/** Status breakdown of the traced group (for the HUD; classification mirrors the renderer's state logic) */
	private static int idleCount, runningCount, doneCount, failedCount;
	/** The group's unified filter item — non-empty only when every target gauge filters the same item; gates the shortage display (a count over mixed items is meaningless). Naming the group by its converged item is planned for a later version. */
	private static ItemStack groupItem = ItemStack.EMPTY;
	/** Σ(target − in storage − in transit) over the group's distinct target gauges; 0 when fully covered or the group's items are mixed */
	private static int shortageCount;
	/** Whether any target gauge has stock links in unloaded chunks (Create's "some links are not loaded" state) */
	private static boolean networkWaiting;

	/** World of the active trace; a dimension change / world exit invalidates it */
	private static Level level;
	private static long lastRefreshMs;
	/** Fade animation: strength interpolates from {@code strengthFrom} to {@code strengthTo} over the fade duration */
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
			// Crosshair on a dyed link within reach → keep the trace alive (and
			// switch color / network when the hovered link is not the traced one)
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

		// Crosshair left the dyed links → fade out immediately (author decision)
		if (!hoveringDyedLink && strengthTo == 1)
			beginFade(now);

		// Periodic refresh while fully active: picks up links added/removed or
		// recolored since the trace started (a stale highlight would otherwise
		// survive as long as the crosshair keeps resting on one link)
		if (strengthTo == 1 && tracedDye != null && now - lastRefreshMs >= REFRESH_MS && !group.isEmpty())
			refresh(world, now);

		// Fade-out finished → drop all state
		if (strengthTo == 0 && strength(now) <= 0.001f)
			hardClear();

		// Replaced traces finish dimming out and expire on their own
		if (!outgoing.isEmpty())
			outgoing.removeIf(trace -> trace.strength(now) <= 0.001f);
	}

	/**
	 * Renderer query: gray-out strength (0..1) for one connection. 0 when
	 * tracing is off, fading out completely, the link belongs to the
	 * highlighted group, <b>or the link lives outside the traced network</b>
	 * — other factories render completely vanilla while a trace is active.
	 * Otherwise the current fade strength: every non-traced line inside the
	 * same network dims equally. While a replaced trace is still dimming out
	 * (a color or network switch), a link takes the <i>maximum</i> of both
	 * layers, so the old group grays in as the new one lights up — both
	 * colors transition smoothly, never a hard swap.
	 */
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
		// A connection's rendering host is its target panel; links of the
		// traced network always target panels inside it
		if (tracedDye != null && !group.isEmpty() && !group.contains(key) && network.contains(to))
			gray = Math.max(gray, strength(now));
		return gray;
	}

	/**
	 * Whether the trace should currently inject its lines into the goggle
	 * overlay — also consulted by the overlay mixin to keep Create's tooltip
	 * alive when the looked-at block contributes no lines of its own. A trace
	 * fading <i>in</i> counts as visible even at strength 0: a color switch
	 * restarts the fade from zero and the HUD must not blink off mid-hover.
	 */
	public static boolean shouldShowTooltip() {
		return tracedDye != null && !group.isEmpty()
			&& (strengthTo == 1 || strength(System.currentTimeMillis()) > 0.001f);
	}

	/**
	 * Appends the trace lines to Create's goggle overlay tooltip as a
	 * two-level list (author decision): a blank separator when the block
	 * contributed its own lines, then the "In this production line:" header
	 * with the color / link / gauge summary indented under it, then the
	 * "Of which:" header with the status counters indented under that — the
	 * tooltip reads like the production line it describes. Status counters
	 * hide zero values and lead with what matters: the failed count in red
	 * ("which of the 40 red links is erroring"), then running, idle, met.
	 * Below them the group's shortage in yellow ("how much does this group
	 * still need" — the restocking shopping list) and Create's own "some
	 * links are not loaded" warning when any target gauge has stock links in
	 * unloaded chunks. Headers sit at the goggle indent every Create goggle
	 * line carries to clear the corner icon; detail lines add another one.
	 */
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

		// Only non-zero counters, most actionable first — a healthy group
		// shows a single short line instead of four numbers
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

		// The group's shopping list, only when every target gauge filters the
		// same item (a bare count over mixed items is meaningless)
		if (shortageCount > 0)
			lines.add(indented(8, statusPart("trace_shortage", shortageCount, ChatFormatting.YELLOW)));

		// Reuses Create's own lang key so the wording matches what the gauge
		// UI already calls this state, in every language Create ships
		if (networkWaiting)
			lines.add(indented(8, Component.translatable("create.factory_panel.some_links_unloaded")
				.withStyle(ChatFormatting.YELLOW)));
	}

	/** One tooltip line at the given indent (spaces), gray like Create's goggle text. */
	private static Component indented(int spaces, Component text) {
		return Component.literal(" ".repeat(spaces)).append(text.copy().withStyle(ChatFormatting.GRAY));
	}

	/** One status counter as a translatable label with its (optionally colored) number. */
	private static Component statusPart(String key, int value, ChatFormatting valueColor) {
		Component number = valueColor == null
			? Component.literal(String.valueOf(value))
			: Component.literal(String.valueOf(value)).withStyle(valueColor);
		return Component.translatable("gui." + ColoredConnections.MODID + "." + key, number);
	}

	/**
	 * Starts (or switches to) a trace of {@code dye}, rooted at the hovered
	 * link's target panel. Switching to a different color or network while a
	 * highlight is visible snapshots the old trace as an outgoing layer and
	 * fades the new group in from zero — a cross-fade, so the old color dims
	 * out exactly as a trace normally ends while the new one lights up
	 * exactly as a trace normally starts. Resuming the same trace (or
	 * starting one while faded out) continues from the current strength
	 * instead of blinking through full-off.
	 */
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

	/** Periodic refresh: re-derive group and stats from the world's current state. */
	private static void refresh(Level world, long now) {
		recompute(world, group.iterator().next().to(), now);
	}

	/**
	 * Rebuilds the trace group: BFS the anchor's connected network (all
	 * links, color ignored), then keep the color table's entries of the
	 * traced dye whose target panel lies inside that network. An empty result
	 * (everything recolored or dismantled) ends the trace.
	 */
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

	/**
	 * Counts the HUD breakdown, reading each group link's target panel state.
	 * "Running" requires items physically in transit: the target panel holds
	 * logistics promises ({@code getPromised() > 0}). A panel with a request
	 * outstanding but nothing promised or moving counts as idle — same as the
	 * gray resting states. Green resting lines (satisfied) are done; the red
	 * restock flash (bulb glowing while unsatisfied and the shipment failed)
	 * is failed.
	 *
	 * <p>Besides the per-link statuses this also derives the per-gauge
	 * aggregates from the group's <i>distinct</i> target panels (a gauge fed
	 * by several group links must not be counted twice): the unified filter
	 * item, the shortage and the unloaded-links warning. Both storage and
	 * promises are synced to clients ({@code lastReportedLevelInStorage} /
	 * {@code lastReportedPromises}), so no extra network traffic is needed.</p>
	 */
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
			// The game's own target: a plain amount counts items, stack mode
			// (upTo == false) counts full stacks
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

	/**
	 * Whether the crosshair's hit point is close enough to start or refresh a
	 * trace: the same block-face point the hover picker used, measured from
	 * the eye. Keeps far-away wall clusters from being traced by accident.
	 */
	private static boolean withinReach(Minecraft mc) {
		HitResult pick = mc.hitResult;
		if (!(pick instanceof BlockHitResult hit) || mc.player == null)
			return false;
		return mc.player.getEyePosition().distanceTo(hit.getLocation())
			<= CreateCCConfig.TRACE_DISTANCE.get();
	}

	/** Begins (or accelerates) the fade-out; a no-op when already fading or idle. */
	private static void beginFade(long now) {
		if (strengthTo == 0)
			return;
		strengthFrom = strength(now);
		strengthTo = 0;
		stateChangeMs = now;
	}

	/** Current fade strength in 0..1, interpolated from the animation state. */
	private static float strength(long now) {
		if (strengthTo == strengthFrom)
			return strengthTo;
		long duration = strengthTo > strengthFrom ? FADE_IN_MS : FADE_OUT_MS;
		float t = Math.min(1f, (now - stateChangeMs) / (float) duration);
		return strengthFrom + (strengthTo - strengthFrom) * t;
	}

	/** Drops every trace state at once (world exit, dimension change, fade finished). */
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

	/**
	 * A snapshot of a trace that was replaced by a newer one (a different
	 * color, or the same color in another factory). Its lines keep dimming to
	 * zero on the fade-out curve while the new trace fades in: the old group
	 * loses its highlight exactly as a trace normally ends, and the new group
	 * gains it exactly as a trace normally starts — a color switch is two
	 * overlapping ordinary transitions instead of a hard swap. The tick
	 * handler drops the snapshot once it has fully dimmed out.
	 */
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

		/** Remaining dim strength of the replaced trace, from its snapshot value down to 0. */
		private float strength(long now) {
			float t = Math.min(1f, (now - startMs) / (float) FADE_OUT_MS);
			return fromStrength * (1 - t);
		}
	}
}
