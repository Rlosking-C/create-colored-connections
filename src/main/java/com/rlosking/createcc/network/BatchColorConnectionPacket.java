package com.rlosking.createcc.network;

import java.util.ArrayList;
import java.util.List;

import com.rlosking.createcc.ColoredConnections;
import com.rlosking.createcc.ConnectionColorManager;
import com.rlosking.createcc.ConnectionKey;
import com.rlosking.createcc.CreateCCConfig;
import com.rlosking.createcc.DyeEffects;
import com.rlosking.createcc.PanelGraph;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BatchColorConnectionPacket(List<FactoryPanelPosition> pathNodes, int dyeOrdinal) implements CustomPacketPayload {

	private static final int MAX_NODES = 64;

	public static final CustomPacketPayload.Type<BatchColorConnectionPacket> TYPE =
		new CustomPacketPayload.Type<>(ColoredConnections.rl("batch_color_connection"));

	public static final StreamCodec<ByteBuf, BatchColorConnectionPacket> STREAM_CODEC = StreamCodec.composite(
		FactoryPanelPosition.STREAM_CODEC.apply(ByteBufCodecs.list()), BatchColorConnectionPacket::pathNodes,
		ByteBufCodecs.VAR_INT, BatchColorConnectionPacket::dyeOrdinal,
		BatchColorConnectionPacket::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(BatchColorConnectionPacket payload, IPayloadContext context) {
		if (!(context.player() instanceof ServerPlayer player))
			return;
		if (!(player.level() instanceof ServerLevel serverLevel))
			return;
		List<FactoryPanelPosition> nodes = payload.pathNodes();
		if (nodes.size() < 2 || nodes.size() > MAX_NODES)
			return;
		for (FactoryPanelPosition node : nodes)
			if (player.position().distanceToSqr(node.pos().getCenter()) > 64 * 64)
				return;
		InteractionHand hand = ColorConnectionPacket.heldDyeHand(player, payload.dyeOrdinal());
		if (hand == null)
			return;
		if (CreateCCConfig.DYE_CONSUMPTION.get() && !player.isCreative())
			player.getItemInHand(hand).shrink(1);

		List<ConnectionKey> path = new ArrayList<>();
		for (int i = 0; i + 1 < nodes.size(); i++) {
			List<ConnectionKey> segment = PanelGraph.pathBetween(serverLevel, nodes.get(i), nodes.get(i + 1));
			if (segment == null)
				return;
			path.addAll(segment);
		}
		if (path.isEmpty())
			return;

		DyeColor dye = payload.dyeOrdinal() < 0 ? null : DyeColor.byId(payload.dyeOrdinal());
		ConnectionColorManager.setColors(serverLevel, path, dye);
		DyeEffects.play(serverLevel, path, dye);
	}
}
