package com.rlosking.createcc.client;

import java.util.List;

import com.rlosking.createcc.ColoredConnections;
import com.rlosking.createcc.ConnectionHitTester;
import com.rlosking.createcc.network.BatchColorConnectionPacket;
import com.rlosking.createcc.network.BatchSelectionModePacket;
import com.rlosking.createcc.network.ColorConnectionPacket;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

/**
 * Entry point of the dye right-click interaction on connection lines.
 *
 * <p>The event fires once on each side: both sides run the same pick test
 * and cancel the vanilla interaction (to avoid clashing with opening the
 * panel config screen etc.) — the client sends the coloring request packet,
 * and the server performs the authoritative validation and persistence when
 * it arrives (validation lives in {@code ColorConnectionPacket#handle}; it
 * is not duplicated here to avoid two copies of the rules drifting apart).
 * Note that cancelling on the client alone never protects anything: the
 * use-item-on packet is sent unconditionally, so the server always fires
 * its own copy of this event and must be talked out of the vanilla
 * interaction on its own.</p>
 *
 * <p>Check order: first whether the held item is a dye (most players hold
 * something else, so the cheapest check comes first), then the more
 * expensive polyline hit scan. On a hit, the client immediately shows an
 * action-bar confirmation; the effective color is whatever the server
 * broadcasts back in its sync packet (a failed server validation leaves no
 * local residue).</p>
 *
 * <p>Every takeover of the gesture cancels the vanilla interaction but
 * reports {@link InteractionResult#SUCCESS} as the cancellation result.
 * Cancelling alone would leave the click looking dead — the client plays the
 * vanilla "use item" feedback (arm swing, item-use animation) only for a
 * result that consumes the action. Reporting SUCCESS keeps the gesture
 * authored by the vanilla pipeline, exactly like Create's own custom
 * right-click handlers do ({@code EdgeInteractionHandler}, {@code LinkHandler}).
 * Cancellation and result are set on both sides so the client and the server
 * agree on what the click was.</p>
 *
 * <p>Black dye semantics = restore the vanilla status color
 * (represented as dyeOrdinal = -1, i.e. clear, in the protocol).</p>
 *
 * <p>Path dyeing: a shift+right-click on a gauge panel always enters path
 * mode and takes priority over single-link dyeing — gauge walls are crowded
 * with lines, so while aiming at a gauge the crosshair usually sits on a
 * line as well, and that line must not steal the click (see
 * handlePathClick). While a chain is being swept, every plain right-click
 * resolves it: on the chain's tail gauge it confirms the batch, anywhere
 * else it cancels (see resolvePendingClick). The server learns about the
 * pending mode through {@link BatchSelectionModePacket} so it can suppress
 * the vanilla gauge click on its side too.</p>
 */
@EventBusSubscriber(modid = ColoredConnections.MODID)
public class DyeInteractionHandler {

	/** Debug log: whether coloring clicks hit, for troubleshooting picking issues in the field */
	private static final Logger LOGGER = LogUtils.getLogger();

	@SubscribeEvent
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		boolean clientSide = event.getLevel().isClientSide();

		// While a chain is pending, EVERY right-click belongs to the chain —
		// whatever is held, and whichever hand holds the dye. Gating this on
		// the interacting hand's dye (as the single-link dyeing below does)
		// let the confirm click slip through to vanilla whenever the dye sat
		// in the offhand: the chain stayed pending, but the click still opened
		// the gauge's config screen on top of the confirmation.
		//
		// The branch also runs before the single-link dyeing below so a pending
		// batch can never be confused with a one-line touch-up, and it cancels
		// the vanilla interaction on BOTH sides: the client's cancellation
		// never reaches the server, which would otherwise open that screen
		// underneath the very click that confirmed the batch.
		{
			// Pending state lives in the client selection on the client side
			// and in the packet-synced mirror on the server side; the ternary
			// short-circuits, so the client-only class is never touched when
			// this runs on a dedicated server
			boolean pending = clientSide ? BatchDyeSelection.hasSelection()
				: event.getEntity() instanceof ServerPlayer serverPlayer
					&& BatchSelectionModePacket.isPending(serverPlayer);
			if (pending && event.getHitVec() != null) {
				FactoryPanelBehaviour clicked = clickedPanel(event);
				event.setCanceled(true);
				event.setCancellationResult(InteractionResult.SUCCESS);
				if (clientSide)
					resolvePendingClick(event.getEntity(), clicked, heldDye(event.getEntity()),
						event.getEntity().isShiftKeyDown());
				return;
			}
		}

		ItemStack stack = event.getItemStack();
		if (!(stack.getItem() instanceof DyeItem dyeItem))
			return;
		if (event.getHitVec() == null)
			return;

		// Shift+right-click with no chain pending starts path dyeing: it takes
		// priority over single-link dyeing because gauge walls are crowded
		// with lines, so while aiming at a gauge the crosshair usually sits on
		// a line as well — without this priority the first shift-click would
		// silently dye that one line and path mode could never even start.
		if (event.getEntity().isShiftKeyDown()) {
			FactoryPanelBehaviour clicked = clickedPanel(event);
			if (clicked != null) {
				// Cancel the vanilla interaction (panel screen, sneak-placing
				// against the panel) so the gesture belongs to path mode; the
				// SUCCESS result keeps the vanilla use feedback (arm swing)
				event.setCanceled(true);
				event.setCancellationResult(InteractionResult.SUCCESS);
				if (clientSide)
					startPath(event.getEntity(), event.getLevel(), clicked);
			}
			// Any other block (or an empty gauge quadrant): leave the
			// interaction to vanilla, so sneak-placing keeps working
			return;
		}

		// Plain right-click on a gauge panel belongs to the gauge — vanilla
		// opens its configuration screen, and the dye must not steal the
		// click even though link lines attach at the panel's own slot and
		// sit within picking range of it. Only clicks on the line itself
		// dye: the raycast then lands on the wall behind the line, not on a
		// panel block.
		if (clickedPanel(event) != null)
			return;

		// Plain right-click: single-link dyeing when the crosshair is on a
		// line. Sticky pick: on the client, seed the picker with the
		// currently hovered connection so the dye lands on the line the
		// player sees lifted (hysteresis inside find() keeps it stable among
		// overlapping lines). The ternary short-circuits on the server, so
		// this client-only class is never touched there.
		ConnectionHoverTracker.Key hoveredKey =
			clientSide ? ConnectionHoverTracker.hoveredConnection() : null;
		ConnectionHitTester.Hit hit = ConnectionHitTester.find(event.getLevel(),
			event.getHitVec().getLocation(), event.getHitVec().getDirection(),
			hoveredKey == null ? null : hoveredKey.from(),
			hoveredKey == null ? null : hoveredKey.to());
		if (clientSide)
			LOGGER.info("createcc coloring click {} -> {}", event.getHitVec().getLocation(),
				hit == null ? "no connection hit" : hit.from().pos() + " -> " + hit.to().pos());
		if (hit == null)
			return;

		// Hit a connection: cancel the vanilla interaction (panel screen, block
		// placement, etc.) and report SUCCESS, so the client still plays the
		// vanilla use feedback (arm swing, item-use animation)
		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);
		if (!clientSide)
			return;

		Player player = event.getEntity();
		DyeColor dye = dyeItem.getDyeColor();
		boolean clear = dye == DyeColor.BLACK;
		PacketDistributor.sendToServer(new ColorConnectionPacket(hit.from(), hit.to(), clear ? -1 : dye.ordinal()));
		player.displayClientMessage(Component.translatable(clear
			? "message.create_colored_connections.cleared"
			: "message.create_colored_connections.dyed")
			.withStyle(ChatFormatting.GREEN), true);
	}

	/**
	 * The active gauge panel at a click position, or null when the block is
	 * not a gauge block — or its targeted quadrant holds no active panel: an
	 * empty quadrant is not a gauge, and a line may still cross it, so line
	 * picking stays enabled there.
	 */
	static FactoryPanelBehaviour panelAt(Level level, BlockPos pos, Vec3 hitLocation) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof FactoryPanelBlock))
			return null;
		// Which of the block's four panel slots the crosshair is on
		FactoryPanelPosition slot = new FactoryPanelPosition(pos,
			FactoryPanelBlock.getTargetedSlot(pos, state, hitLocation));
		FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(level, slot);
		return behaviour != null && behaviour.isActive() ? behaviour : null;
	}

	/** The panel the block-interaction event landed on; see {@link #panelAt}. */
	private static FactoryPanelBehaviour clickedPanel(PlayerInteractEvent.RightClickBlock event) {
		return panelAt(event.getLevel(), event.getPos(), event.getHitVec().getLocation());
	}

	/**
	 * Starts a path-dyeing chain on the clicked gauge (client only). The chain
	 * is then grown by the tick sampler as the crosshair sweeps over further
	 * gauges ({@link BatchDyeSelection#step}), or explicitly by clicking them —
	 * a missed sweep is always fixable with a click (see
	 * {@link #resolvePendingClick}).
	 */
	private static void startPath(Player player, Level level, FactoryPanelBehaviour clicked) {
		if (BatchDyeSelection.hasSelection())
			return;
		BatchDyeSelection.select(level, clicked.getPanelPosition());
		player.displayClientMessage(Component.translatable("message.create_colored_connections.path_start")
			.withStyle(ChatFormatting.GREEN), true);
	}

	/**
	 * Resolves a chain-building right-click (client only), with the semantics
	 * of Create's connection flow: {@code aborts} (shift held) always cancels,
	 * a click that landed on no gauge cancels, a click on the start gauge
	 * cancels, a click on the tail confirms the batch, and a click on any
	 * other gauge appends it to the chain. Appending reports failures — a
	 * gauge that cannot join explains why instead of doing nothing.
	 *
	 * <p>Called from {@link PathDyeInputHandler}, which owns the use key while
	 * a chain is pending, and from the block-interaction fallback below.</p>
	 */
	static void resolvePendingClick(Player player, FactoryPanelBehaviour clicked, DyeItem dyeItem, boolean aborts) {
		if (aborts || clicked == null) {
			LOGGER.info("createcc path click -> cancel ({})", aborts ? "shift held" : "no gauge under crosshair");
			cancelPath(player);
			return;
		}
		// Nothing to apply: the chain is dropped with the reason spelled out
		// (the tick handler would end it a moment later, without the context
		// of the click that was meant to confirm it)
		if (dyeItem == null) {
			LOGGER.info("createcc path click -> cancel (no dye in either hand)");
			BatchDyeSelection.clear();
			player.displayClientMessage(Component.translatable(
				"message.create_colored_connections.path_no_dye"), true);
			return;
		}
		FactoryPanelPosition slot = clicked.getPanelPosition();

		// Clicking the chain's start again drops the whole chain — Create's
		// "click the source panel to clear it". With a single-gauge chain the
		// start IS the tail, so this also covers confirming nothing.
		if (slot.equals(BatchDyeSelection.start())) {
			LOGGER.info("createcc path click -> cancel (clicked the start gauge {})", slot.pos());
			cancelPath(player);
			return;
		}

		if (BatchDyeSelection.size() >= 2 && slot.equals(BatchDyeSelection.tail())) {
			List<FactoryPanelPosition> nodes = BatchDyeSelection.nodes();
			DyeColor dye = dyeItem.getDyeColor();
			boolean clear = dye == DyeColor.BLACK;
			// clear() queues the mode-end sync one tick later, so the server
			// still suppresses the vanilla side of THIS click; the batch
			// packet itself goes out right here
			BatchDyeSelection.clear();
			PacketDistributor.sendToServer(new BatchColorConnectionPacket(nodes, clear ? -1 : dye.ordinal()));
			LOGGER.info("createcc path click -> confirm {} gauges as {}", nodes.size(), dye);
			player.displayClientMessage(Component.translatable(clear
				? "message.create_colored_connections.path_cleared"
				: "message.create_colored_connections.path_dyed")
				.withStyle(ChatFormatting.GREEN), true);
			return;
		}

		// Any other gauge: the same step the sweep sampler performs, but with
		// reporting on, so a gauge that cannot join says why
		LOGGER.info("createcc path click -> append {}", slot.pos());
		BatchDyeSelection.step(player.level(), slot, true);
	}

	private static void cancelPath(Player player) {
		BatchDyeSelection.clear();
		player.displayClientMessage(Component.translatable("message.create_colored_connections.path_cancel"), true);
	}

	/**
	 * The dye a confirmation would apply: the main hand's, or the offhand's
	 * when the main hand holds something else — the same pair
	 * {@code BatchDyeSelection} accepts for keeping a chain alive. Null when
	 * neither hand holds a dye.
	 */
	static DyeItem heldDye(Player player) {
		if (player.getMainHandItem().getItem() instanceof DyeItem main)
			return main;
		if (player.getOffhandItem().getItem() instanceof DyeItem off)
			return off;
		return null;
	}
}
