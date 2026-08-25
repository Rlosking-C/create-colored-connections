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

/**
 * Client → Server: "a path-dye selection is now active / just ended".
 *
 * <p>The chain of swept gauges itself is pure client state — the server
 * never needs it, because the confirming click re-sends the whole node
 * list in {@link BatchColorConnectionPacket} and the server re-derives
 * every segment from its own world state. What the server DOES need is
 * the boolean: while a selection is pending, a plain dye right-click on
 * a gauge means "confirm" or "cancel" instead of vanilla's "open the
 * panel screen", and that vanilla interaction must be suppressed on the
 * server side too — the client's event cancellation never reaches it
 * (the use-item-on packet is sent unconditionally, so the server always
 * fires its own copy of the event).</p>
 *
 * <p>The client sends {@code active = true} when the selection starts and
 * {@code active = false} one tick AFTER it ends. The delay is a race fix:
 * ending the selection and confirming it happen inside the same click, so
 * an immediate {@code false} would land before the click's use-item-on
 * packet and re-open the panel screen the confirm just suppressed.</p>
 */
public record BatchSelectionModePacket(boolean active) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<BatchSelectionModePacket> TYPE =
		new CustomPacketPayload.Type<>(ColoredConnections.rl("batch_selection_mode"));

	public static final StreamCodec<ByteBuf, BatchSelectionModePacket> STREAM_CODEC =
		ByteBufCodecs.BOOL.map(BatchSelectionModePacket::new, BatchSelectionModePacket::active);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	/**
	 * Server-side mirror of "this player is mid-selection", keyed by player
	 * UUID so it survives the entity instance being replaced (dimension
	 * travel). Written only by this packet's handler; read by the
	 * interaction handler on the server thread.
	 */
	private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();

	/** Records the mode on the server (called when the packet lands). */
	public static void setPending(ServerPlayer player, boolean active) {
		if (active)
			PENDING.add(player.getUUID());
		else
			PENDING.remove(player.getUUID());
	}

	/** Whether the player is mid-selection, per the client's last sync. */
	public static boolean isPending(ServerPlayer player) {
		return PENDING.contains(player.getUUID());
	}

	/** Drops the mirror entry (logout) so a returning player starts clean. */
	public static void forget(ServerPlayer player) {
		PENDING.remove(player.getUUID());
	}

	public static void handle(BatchSelectionModePacket payload, IPayloadContext context) {
		if (context.player() instanceof ServerPlayer player)
			setPending(player, payload.active());
	}
}
