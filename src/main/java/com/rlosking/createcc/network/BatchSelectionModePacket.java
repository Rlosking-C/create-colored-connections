package com.rlosking.createcc.network;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.rlosking.createcc.ColoredConnections;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BatchSelectionModePacket(boolean active) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<BatchSelectionModePacket> TYPE =
		new CustomPacketPayload.Type<>(ColoredConnections.rl("batch_selection_mode"));

	public static final StreamCodec<ByteBuf, BatchSelectionModePacket> STREAM_CODEC =
		ByteBufCodecs.BOOL.map(BatchSelectionModePacket::new, BatchSelectionModePacket::active);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();

	public static void setPending(ServerPlayer player, boolean active) {
		if (active)
			PENDING.add(player.getUUID());
		else
			PENDING.remove(player.getUUID());
	}

	public static boolean isPending(ServerPlayer player) {
		return PENDING.contains(player.getUUID());
	}

	public static void forget(ServerPlayer player) {
		PENDING.remove(player.getUUID());
	}

	public static void handle(BatchSelectionModePacket payload, IPayloadContext context) {
		if (context.player() instanceof ServerPlayer player)
			setPending(player, payload.active());
	}
}
