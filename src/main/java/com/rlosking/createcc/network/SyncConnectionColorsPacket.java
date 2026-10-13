package com.rlosking.createcc.network;

import java.util.List;

import com.rlosking.createcc.ColoredConnections;
import com.rlosking.createcc.ConnectionColorManager;
import com.rlosking.createcc.ConnectionKey;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncConnectionColorsPacket(List<Entry> entries, boolean fullSync) implements CustomPacketPayload {

	public record Entry(ConnectionKey key, int dyeOrdinal) {
		public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
			ConnectionKey.STREAM_CODEC, Entry::key,
			ByteBufCodecs.VAR_INT, Entry::dyeOrdinal,
			Entry::new
		);
	}

	public static final CustomPacketPayload.Type<SyncConnectionColorsPacket> TYPE =
		new CustomPacketPayload.Type<>(ColoredConnections.rl("sync_colors"));

	public static final StreamCodec<ByteBuf, SyncConnectionColorsPacket> STREAM_CODEC = StreamCodec.composite(
		Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncConnectionColorsPacket::entries,
		ByteBufCodecs.BOOL, SyncConnectionColorsPacket::fullSync,
		SyncConnectionColorsPacket::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(SyncConnectionColorsPacket payload, IPayloadContext context) {
		if (payload.fullSync())
			ConnectionColorManager.clientReplaceAll(payload.entries());
		else
			payload.entries().forEach(entry -> ConnectionColorManager.clientApply(entry.key(), entry.dyeOrdinal()));
	}
}
