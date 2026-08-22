package com.colconn.createcc.network;

import com.colconn.createcc.ColoredConnections;
import com.colconn.createcc.ConnectionColorManager;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → Server: a player's request to color one connection.
 *
 * <p>The server triple-validates: player identity, distance (prevents
 * remote cheat-coloring), and that the matching dye is actually held;
 * in non-creative mode one dye is consumed. Persistence and broadcasting
 * are handled by {@link ConnectionColorManager#setColor}.</p>
 */
public record ColorConnectionPacket(FactoryPanelPosition from, FactoryPanelPosition to, int dyeOrdinal) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<ColorConnectionPacket> TYPE =
		new CustomPacketPayload.Type<>(ColoredConnections.rl("color_connection"));

	public static final StreamCodec<ByteBuf, ColorConnectionPacket> STREAM_CODEC = StreamCodec.composite(
		FactoryPanelPosition.STREAM_CODEC, ColorConnectionPacket::from,
		FactoryPanelPosition.STREAM_CODEC, ColorConnectionPacket::to,
		ByteBufCodecs.VAR_INT, ColorConnectionPacket::dyeOrdinal,
		ColorConnectionPacket::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	/**
	 * Server handler: validate distance and held dye, then persist and broadcast.
	 *
	 * <p>Validation is independent of the client-side picking done by the
	 * renderer mixin — packet contents are untrusted, server state is the
	 * single source of truth. Any failed check drops the packet silently
	 * (no error packet back); the client's optimistic action-bar feedback
	 * simply never becomes real, so no extra failure protocol is needed.</p>
	 */
	public static void handle(ColorConnectionPacket payload, IPayloadContext context) {
		if (!(context.player() instanceof ServerPlayer player))
			return;
		if (!(player.level() instanceof ServerLevel serverLevel))
			return;
		// Validation: the player must be within a reasonable range of the target panel
		// (same order of magnitude as the gauge interaction distance)
		if (player.position().distanceToSqr(payload.to().pos().getCenter()) > 64 * 64)
			return;
		// Validation: the matching dye must actually be held (black = clear-color
		// semantics); consume one in non-creative mode
		if (!consumeDye(player, payload.dyeOrdinal()))
			return;
		DyeColor dye = payload.dyeOrdinal() < 0 ? null : DyeColor.byId(payload.dyeOrdinal());
		ConnectionColorManager.setColor(serverLevel, payload.from(), payload.to(), dye);
	}

	/**
	 * Finds a dye matching the request in either hand; black corresponds to
	 * dyeOrdinal = -1 (clear the color).
	 */
	private static boolean consumeDye(ServerPlayer player, int dyeOrdinal) {
		if (player.isCreative())
			return true;
		DyeColor expected = dyeOrdinal < 0 ? DyeColor.BLACK : DyeColor.byId(dyeOrdinal);
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if (held.getItem() instanceof DyeItem dyeItem && dyeItem.getDyeColor() == expected) {
				held.shrink(1);
				return true;
			}
		}
		return false;
	}
}
