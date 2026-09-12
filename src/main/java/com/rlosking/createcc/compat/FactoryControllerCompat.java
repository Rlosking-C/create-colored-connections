package com.rlosking.createcc.compat;

import com.rlosking.createcc.CreateCCConfig;
import com.rlosking.createcc.network.ColorConnectionPacket;
import com.rlosking.createcc.network.FCColorConnectionPacket;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionKey;
import io.github.nbcss.createfactorycontroller.content.component.connection.LogisticsConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server side of the Factory Controller GUI dyeing integration.
 *
 * <p>This class is the ONLY place (outside the mixin package) that imports
 * Factory Controller classes, so it must never be class-loaded when the mod
 * is absent: every entry point first checks {@link #isLoaded()} and callers
 * keep their own code FC-free. The same rules as the physical-world dyeing
 * apply: the matching dye must be held, it is never consumed unless the
 * {@code dyeConsumption} config says so, and only ingredient
 * ({@code LOGISTICS}) wires are colorable — redstone and number wires keep
 * their status-semantic colors, exactly like redstone/display links are
 * excluded in the world.</p>
 */
public final class FactoryControllerCompat {

	private static final org.apache.logging.log4j.Logger LOGGER =
			org.apache.logging.log4j.LogManager.getLogger("createcc-fc");

	private FactoryControllerCompat() {}

	/** @return whether Create: Factory Controller is installed on this side */
	public static boolean isLoaded() {
		return ModList.get().isLoaded("createfactorycontroller");
	}

	/**
	 * Applies (or clears) the dye on one virtual ingredient wire.
	 *
	 * <p>Validation chain, mirroring {@link ColorConnectionPacket}'s rules:
	 * the player must have this controller's GUI open (the open menu doubles
	 * as proof of proximity — FactoryControllerMenu.stillValid enforces the
	 * 8-block range), must hold the matching dye, and the addressed wire must
	 * exist on the server's connection graph. The dye then lives on the
	 * connection object itself, so FC's own persistence (world NBT) and delta
	 * sync (writeClient bytes) carry it to storage and every GUI viewer
	 * without any custom packet of ours.</p>
	 */
	public static void dyeConnection(FCColorConnectionPacket payload, IPayloadContext context) {
		// Server-side gate of the same config the client checks before sending
		if (!CreateCCConfig.FACTORY_CONTROLLER_DYEING.get())
			return;
		if (!(context.player() instanceof ServerPlayer player)) {
			LOGGER.info("[createcc-fc] server: not a server player");
			return;
		}
		// The open menu must be the controller being addressed (anti-spoof:
		// a client can only dye the board it is actually looking at)
		if (!(player.containerMenu instanceof FactoryControllerMenu menu)) {
			LOGGER.info("[createcc-fc] server: open menu is {}",
				player.containerMenu.getClass().getName());
			return;
		}
		BlockPos controllerPos = menu.controllerPos;
		if (!controllerPos.equals(payload.pos())) {
			LOGGER.info("[createcc-fc] server: pos mismatch {} vs {}", controllerPos, payload.pos());
			return;
		}
		// Dye source, matching the client's precedence: the GUI flow carries the
		// dye on the cursor (left-click pickup, right-click on the wire); a
		// world-style hand-held dye also works. Black = clear; outside creative
		// the source must match the requested color, and the optional cost
		// charges whichever stack provided it.
		DyeColor expected = payload.dyeOrdinal() < 0 ? DyeColor.BLACK : DyeColor.byId(payload.dyeOrdinal());
		ItemStack cursorDye = menu.getCarried();
		ItemStack dyeSource = null;
		if (cursorDye.getItem() instanceof DyeItem carriedDye
				&& (player.isCreative() || carriedDye.getDyeColor() == expected)) {
			dyeSource = cursorDye;
		} else {
			InteractionHand hand = ColorConnectionPacket.heldDyeHand(player, payload.dyeOrdinal());
			if (hand != null)
				dyeSource = player.getItemInHand(hand);
		}
		if (dyeSource == null) {
			LOGGER.info("[createcc-fc] server: no matching dye on cursor or in hands (ordinal={})",
				payload.dyeOrdinal());
			return;
		}
		if (!(player.level().getBlockEntity(controllerPos) instanceof FactoryControllerBlockEntity be)) {
			LOGGER.info("[createcc-fc] server: no controller BE at {}", controllerPos);
			return;
		}
		// Only ingredient wires are dyeable (redstone/number wires excluded,
		// same rule as redstone/display links in the world)
		if (Connection.Type.get(payload.typeName()) != LogisticsConnection.TYPE) {
			LOGGER.info("[createcc-fc] server: type {} not dyeable", payload.typeName());
			return;
		}
		Connection conn = be.connectionGraph().get(
			new VirtualComponentPosition(payload.fromX(), payload.fromY()),
			new VirtualComponentPosition(payload.toX(), payload.toY()),
			LogisticsConnection.TYPE);
		if (!(conn instanceof LogisticsConnection wire)) {
			LOGGER.info("[createcc-fc] server: wire not found ({},{}) -> ({},{})",
				payload.fromX(), payload.fromY(), payload.toX(), payload.toY());
			return;
		}
		int dye = payload.dyeOrdinal() < 0 ? 0 : payload.dyeOrdinal() + 1;
		if (((VirtualConnectionDye) wire).createcc$getDye() == dye) {
			LOGGER.info("[createcc-fc] server: wire already has dye {}", dye);
			return;
		}
		((VirtualConnectionDye) wire).createcc$setDye(dye);
		// Optional dye cost, same config and survival-only rule as world dyeing;
		// charged from the cursor stack or the hand, whichever provided the color
		if (CreateCCConfig.DYE_CONSUMPTION.get() && !player.isCreative())
			dyeSource.shrink(1);
		// Persist the board and push the updated wire to every GUI viewer
		be.setChanged();
		be.syncConnection(ConnectionKey.of(wire));
		LOGGER.info("[createcc-fc] server: dye {} applied and synced", dye);
		// Same feedback sound family as world dyeing (Create's gauge-link cue),
		// heard by anyone standing at the controller
		if (CreateCCConfig.DYE_EFFECTS.get())
			player.level().playSound(null, controllerPos,
				SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 0.5f, 1.0f);
	}
}
