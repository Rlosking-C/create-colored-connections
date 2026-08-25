package com.rlosking.createcc.network;

import java.util.ArrayList;
import java.util.List;

import com.rlosking.createcc.ConnectionColorManager;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Network-layer registration and color data synchronization.
 *
 * <p>Sync strategy:
 * <ul>
 *   <li>Incremental: when a connection is colored or cleared, broadcast to
 *       players tracking the chunk containing the target panel (see setColor)</li>
 *   <li>Full: on login and dimension change the whole table is sent; the
 *       client mirror is cleared before landing</li>
 *   <li>Catch-up: when a player starts tracking a chunk, that chunk's
 *       connection colors are re-sent, covering the "colored while the player
 *       was far away, walked closer and loaded it later" scenario</li>
 * </ul></p>
 */
public class ColoredConnectionNetwork {

	/**
	 * Registers the custom payloads (standard NeoForge 1.21.1 PayloadRegistrar
	 * flow). Attached to the mod bus by the mod constructor; class annotations
	 * are avoided to prevent bus ambiguity.
	 */
	public static void register(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("1");
		registrar.playToClient(SyncConnectionColorsPacket.TYPE, SyncConnectionColorsPacket.STREAM_CODEC,
			SyncConnectionColorsPacket::handle);
		registrar.playToServer(ColorConnectionPacket.TYPE, ColorConnectionPacket.STREAM_CODEC,
			ColorConnectionPacket::handle);
		registrar.playToServer(BatchColorConnectionPacket.TYPE, BatchColorConnectionPacket.STREAM_CODEC,
			BatchColorConnectionPacket::handle);
		registrar.playToServer(BatchSelectionModePacket.TYPE, BatchSelectionModePacket.STREAM_CODEC,
			BatchSelectionModePacket::handle);
	}

	/**
	 * Full sync: sends every color entry of the player's dimension
	 * (the fullSync flag makes the client clear its mirror first).
	 */
	static void syncAll(ServerPlayer player) {
		List<SyncConnectionColorsPacket.Entry> entries =
			ConnectionColorManager.snapshot(player.server, player.level().dimension());
		player.connection.send(new SyncConnectionColorsPacket(entries, true));
	}

	/**
	 * Game-bus events: full sync on login / dimension change, incremental
	 * catch-up on chunk watch. Kept as a nested class: @EventBusSubscriber
	 * defaults to the game bus, keeping it separate from the mod-bus
	 * registration above.
	 */
	@EventBusSubscriber(modid = com.rlosking.createcc.ColoredConnections.MODID)
	public static class GameEvents {

		/**
		 * Player login: send the full color table of their dimension.
		 */
		@SubscribeEvent
		public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
			if (event.getEntity() instanceof ServerPlayer player)
				syncAll(player);
		}

		/**
		 * Player logout: drop the batch-selection mode mirror so a returning
		 * player starts clean (their client's "selection ended" sync may
		 * never have made it out before the disconnect).
		 */
		@SubscribeEvent
		public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
			if (event.getEntity() instanceof ServerPlayer player)
				BatchSelectionModePacket.forget(player);
		}

		/**
		 * Dimension change: the client mirror is now stale (it still holds the
		 * old dimension's data), so re-send the new dimension's full table.
		 */
		@SubscribeEvent
		public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
			if (event.getEntity() instanceof ServerPlayer player)
				syncAll(player);
		}

		/**
		 * Player starts tracking a chunk: re-send the colors of connections
		 * whose target panel lives in that chunk.
		 *
		 * <p>Scenario: player B colors something while player A is far away
		 * (A doesn't track that chunk, so A missed the incremental broadcast);
		 * later A walks closer and the chunk loads — the catch-up now restores
		 * A's mirror to completeness.</p>
		 */
		@SubscribeEvent
		public static void onChunkWatch(ChunkWatchEvent.Watch event) {
			ServerPlayer player = event.getPlayer();
			ChunkPos watched = event.getPos();
			List<SyncConnectionColorsPacket.Entry> entries =
				ConnectionColorManager.snapshot(player.server, player.level().dimension());
			List<SyncConnectionColorsPacket.Entry> inChunk = new ArrayList<>();
			for (SyncConnectionColorsPacket.Entry entry : entries)
				if (new ChunkPos(entry.key().to().pos()).equals(watched))
					inChunk.add(entry);
			if (!inChunk.isEmpty())
				player.connection.send(new SyncConnectionColorsPacket(inChunk, false));
		}
	}
}
