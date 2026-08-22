package com.colconn.createcc.network;

import java.util.List;

import com.colconn.createcc.ColoredConnections;
import com.colconn.createcc.ConnectionColorManager;
import com.colconn.createcc.ConnectionKey;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → Client: connection color sync packet.
 *
 * <p>Two scenarios:
 * <ul>
 *   <li>Incremental (fullSync = false): when one connection is colored or
 *       cleared, broadcast to players tracking the chunk containing the panel</li>
 *   <li>Full (fullSync = true): on player login or dimension change, clear
 *       the client mirror first and then land the whole table, so stale
 *       entries from the old dimension can't pollute the new one</li>
 * </ul></p>
 */
public record SyncConnectionColorsPacket(List<Entry> entries, boolean fullSync) implements CustomPacketPayload {

	/** One color record; dyeOrdinal = -1 means clearing that connection's color */
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

	/**
	 * Client handler: on fullSync replace the mirror wholesale, otherwise
	 * apply entries incrementally.
	 */
	public static void handle(SyncConnectionColorsPacket payload, IPayloadContext context) {
		if (payload.fullSync())
			ConnectionColorManager.clientReplaceAll(payload.entries());
		else
			payload.entries().forEach(entry -> ConnectionColorManager.clientApply(entry.key(), entry.dyeOrdinal()));
	}
}
