package com.rlosking.createcc.network;

import com.rlosking.createcc.ColoredConnections;
import com.rlosking.createcc.compat.FactoryControllerCompat;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → Server: a player's request to dye one ingredient wire inside a
 * Factory Controller GUI.
 *
 * <p>The wire is addressed the way Factory Controller's own
 * {@code RemoveConnectionPacket} addresses it — controller position, both
 * board endpoints and the connection type name — but expressed as plain
 * ints/strings so this class never imports Factory Controller types. That
 * keeps the payload registrable (and loadable) even when the mod is absent;
 * the actual application lives behind {@link FactoryControllerCompat#isLoaded()}.</p>
 */
public record FCColorConnectionPacket(BlockPos pos, int fromX, int fromY, int toX, int toY,
		String typeName, int dyeOrdinal) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<FCColorConnectionPacket> TYPE =
		new CustomPacketPayload.Type<>(ColoredConnections.rl("fc_color_connection"));

	/**
	 * One board endpoint, packed as its own value so the outer composite
	 * stays within StreamCodec's six-field limit.
	 */
	record WireEnd(int x, int y) {
		static final StreamCodec<ByteBuf, WireEnd> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, WireEnd::x,
			ByteBufCodecs.VAR_INT, WireEnd::y,
			WireEnd::new
		);
	}

	public static final StreamCodec<ByteBuf, FCColorConnectionPacket> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, FCColorConnectionPacket::pos,
		WireEnd.CODEC, FCColorConnectionPacket::from,
		WireEnd.CODEC, FCColorConnectionPacket::to,
		ByteBufCodecs.STRING_UTF8, FCColorConnectionPacket::typeName,
		ByteBufCodecs.VAR_INT, FCColorConnectionPacket::dyeOrdinal,
		FCColorConnectionPacket::new
	);

	public WireEnd from() {
		return new WireEnd(fromX, fromY);
	}

	public WireEnd to() {
		return new WireEnd(toX, toY);
	}

	/** Constructor matching the composite codec's five components. */
	public FCColorConnectionPacket(BlockPos pos, WireEnd from, WireEnd to,
			String typeName, int dyeOrdinal) {
		this(pos, from.x, from.y, to.x, to.y, typeName, dyeOrdinal);
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	/**
	 * Server handler: delegates into the Factory Controller compat layer.
	 *
	 * <p>Like {@link ColorConnectionPacket}, failed validations drop the
	 * packet silently — the client does no optimistic feedback for GUI
	 * dyeing, so a rejected dye simply never shows up on the wire.</p>
	 */
	public static void handle(FCColorConnectionPacket payload, IPayloadContext context) {
		if (!FactoryControllerCompat.isLoaded())
			return;
		FactoryControllerCompat.dyeConnection(payload, context);
	}
}
