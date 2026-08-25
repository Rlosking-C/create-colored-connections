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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
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
		ItemStack stack = event.getItemStack();
		if (!(stack.getItem() instanceof DyeItem dyeItem))
			return;
		if (event.getHitVec() == null)
			return;

		boolean clientSide = event.getLevel().isClientSide();

		// While a chain is pending, every plain right-click resolves it:
		// on the tail gauge it confirms the batch, anywhere else — another
		// gauge, a link line, a wall, the floor — it cancels. This branch
		// runs before the single-link dyeing below so a pending batch can
		// never be confused with a one-line touch-up, and it cancels the
		// vanilla interaction on BOTH sides: the client's cancellation never
		// reaches the server, which would otherwise open the gauge's config
		// screen underneath the very click that confirmed the batch.
		if (!event.getEntity().isShiftKeyDown()) {
			// Pending state lives in the client selection on the client side
			// and in the packet-synced mirror on the server side; the ternary
			// short-circuits, so the client-only class is never touched when
			// this runs on a dedicated server
			boolean pending = clientSide ? BatchDyeSelection.hasSelection()
				: event.getEntity() instanceof ServerPlayer serverPlayer
					&& BatchSelectionModePacket.isPending(serverPlayer);
			if (pending) {
				FactoryPanelBehaviour clicked = clickedPanel(event);
				event.setCanceled(true);
				if (clientSide)
					resolvePendingClick(event.getEntity(), clicked, dyeItem);
				return;
			}
		}

		// Shift+right-click: path dyeing takes priority over single-link
		// dyeing. Gauge walls are crowded with lines, so while aiming at a
		// gauge the crosshair usually sits on a line as well — without this
		// priority the first shift-click would silently dye that one line
		// and path mode could never even start.
		if (event.getEntity().isShiftKeyDown()) {
			FactoryPanelBehaviour clicked = clickedPanel(event);
			if (clicked != null) {
				// Cancel the vanilla interaction (panel screen, sneak-placing
				// against the panel) so the gesture belongs to path mode
				event.setCanceled(true);
				if (clientSide)
					handlePathClick(event.getEntity(), event.getLevel(), clicked);
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

		// Hit a connection: cancel the vanilla interaction (panel screen, block placement, etc.)
		event.setCanceled(true);
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
	 * The active gauge panel the click landed on, or null when the clicked
	 * block is not a gauge block — or its targeted quadrant holds no active
	 * panel: an empty quadrant is not a gauge, and a line may still cross it,
	 * so line picking stays enabled there.
	 */
	private static FactoryPanelBehaviour clickedPanel(PlayerInteractEvent.RightClickBlock event) {
		BlockPos pos = event.getPos();
		BlockState state = event.getLevel().getBlockState(pos);
		if (!(state.getBlock() instanceof FactoryPanelBlock))
			return null;
		// Which of the block's four panel slots the crosshair is on
		FactoryPanelPosition slot = new FactoryPanelPosition(pos,
			FactoryPanelBlock.getTargetedSlot(pos, state, event.getHitVec().getLocation()));
		FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(event.getLevel(), slot);
		return behaviour != null && behaviour.isActive() ? behaviour : null;
	}

	/**
	 * Path-dyeing chain building (client only): the first shift+right-click
	 * on a gauge starts the chain, further ones mirror a hover step — a new
	 * gauge is appended, the current tail is dropped again (undo). The chain
	 * itself is grown by the tick sampler as the crosshair sweeps over
	 * gauges (see {@link BatchDyeSelection#step}); the click is the reliable
	 * fallback for a gauge the sweep skipped.
	 */
	private static void handlePathClick(Player player, Level level, FactoryPanelBehaviour clicked) {
		FactoryPanelPosition slot = clicked.getPanelPosition();

		if (!BatchDyeSelection.hasSelection()) {
			BatchDyeSelection.select(level, slot);
			player.displayClientMessage(Component.translatable("message.create_colored_connections.path_start")
				.withStyle(ChatFormatting.GREEN), true);
			return;
		}
		// Clicking the lone start gauge again cancels (nothing is chained
		// yet); with a longer chain the click is just another sweep step
		if (BatchDyeSelection.size() == 1 && slot.equals(BatchDyeSelection.tail())) {
			BatchDyeSelection.clear();
			player.displayClientMessage(Component.translatable("message.create_colored_connections.path_cancel"), true);
			return;
		}
		BatchDyeSelection.step(level, slot);
	}

	/**
	 * Resolves a pending chain (client only): a plain right-click on the
	 * chain's tail gauge sends the batch request, anything else — blank
	 * space, a wall, a link line, another gauge — cancels the selection.
	 * A lone start node has nothing to dye, so confirming "it" is also just
	 * a cancel.
	 */
	private static void resolvePendingClick(Player player, FactoryPanelBehaviour clicked, DyeItem dyeItem) {
		FactoryPanelPosition slot = clicked == null ? null : clicked.getPanelPosition();

		if (slot != null && BatchDyeSelection.size() >= 2 && slot.equals(BatchDyeSelection.tail())) {
			List<FactoryPanelPosition> nodes = BatchDyeSelection.nodes();
			DyeColor dye = dyeItem.getDyeColor();
			boolean clear = dye == DyeColor.BLACK;
			// clear() queues the mode-end sync one tick later, so the server
			// still suppresses the vanilla side of THIS click; the batch
			// packet itself goes out right here
			BatchDyeSelection.clear();
			PacketDistributor.sendToServer(new BatchColorConnectionPacket(nodes, clear ? -1 : dye.ordinal()));
			player.displayClientMessage(Component.translatable(clear
				? "message.create_colored_connections.path_cleared"
				: "message.create_colored_connections.path_dyed")
				.withStyle(ChatFormatting.GREEN), true);
			return;
		}
		BatchDyeSelection.clear();
		player.displayClientMessage(Component.translatable("message.create_colored_connections.path_cancel"), true);
	}
}
