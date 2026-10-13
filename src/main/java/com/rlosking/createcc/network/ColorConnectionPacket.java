package com.rlosking.createcc.network;

import com.rlosking.createcc.ColoredConnections;
import com.rlosking.createcc.ConnectionColorManager;
import com.rlosking.createcc.CreateCCConfig;
import com.rlosking.createcc.DyeEffects;
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

	public static void handle(ColorConnectionPacket payload, IPayloadContext context) {
		if (!(context.player() instanceof ServerPlayer player))
			return;
		if (!(player.level() instanceof ServerLevel serverLevel))
			return;
		if (player.position().distanceToSqr(payload.to().pos().getCenter()) > 64 * 64)
			return;
		InteractionHand hand = heldDyeHand(player, payload.dyeOrdinal());
		if (hand == null)
			return;
		if (CreateCCConfig.DYE_CONSUMPTION.get() && !player.isCreative())
			player.getItemInHand(hand).shrink(1);
		DyeColor dye = payload.dyeOrdinal() < 0 ? null : DyeColor.byId(payload.dyeOrdinal());
		ConnectionColorManager.setColor(serverLevel, payload.from(), payload.to(), dye);
		DyeEffects.play(serverLevel, payload.from(), payload.to(), dye);
	}

	public static InteractionHand heldDyeHand(ServerPlayer player, int dyeOrdinal) {
		if (player.isCreative())
			return InteractionHand.MAIN_HAND;
		DyeColor expected = dyeOrdinal < 0 ? DyeColor.BLACK : DyeColor.byId(dyeOrdinal);
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if (held.getItem() instanceof DyeItem dyeItem && dyeItem.getDyeColor() == expected)
				return hand;
		}
		return null;
	}
}
