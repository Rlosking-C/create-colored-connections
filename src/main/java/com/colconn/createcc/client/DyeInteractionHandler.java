package com.colconn.createcc.client;

import com.colconn.createcc.ColoredConnections;
import com.colconn.createcc.ConnectionHitTester;
import com.colconn.createcc.network.ColorConnectionPacket;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
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
 * is not duplicated here to avoid two copies of the rules drifting apart).</p>
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

		// Sticky pick: on the client, seed the picker with the currently
		// hovered connection so the dye lands on the line the player sees
		// lifted (hysteresis inside find() keeps it stable among overlapping
		// lines). The ternary short-circuits on the server, so this
		// client-only class is never touched there.
		boolean clientSide = event.getLevel().isClientSide();
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

		if (!event.getLevel().isClientSide())
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
}
