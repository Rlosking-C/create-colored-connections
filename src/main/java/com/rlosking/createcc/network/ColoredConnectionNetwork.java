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

public class ColoredConnectionNetwork {

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
		registrar.playToServer(FCColorConnectionPacket.TYPE, FCColorConnectionPacket.STREAM_CODEC,
			FCColorConnectionPacket::handle);
	}

	static void syncAll(ServerPlayer player) {
		List<SyncConnectionColorsPacket.Entry> entries =
			ConnectionColorManager.snapshot(player.server, player.level().dimension());
		player.connection.send(new SyncConnectionColorsPacket(entries, true));
	}

	@EventBusSubscriber(modid = com.rlosking.createcc.ColoredConnections.MODID)
	public static class GameEvents {

		@SubscribeEvent
		public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
			if (event.getEntity() instanceof ServerPlayer player)
				syncAll(player);
		}

		@SubscribeEvent
		public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
			if (event.getEntity() instanceof ServerPlayer player)
				BatchSelectionModePacket.forget(player);
		}

		@SubscribeEvent
		public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
			if (event.getEntity() instanceof ServerPlayer player)
				syncAll(player);
		}

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
