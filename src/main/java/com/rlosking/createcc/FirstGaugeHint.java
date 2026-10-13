package com.rlosking.createcc;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = ColoredConnections.MODID)
public class FirstGaugeHint {

	private static final String SEEN_FLAG = "createcc_gauge_hint_seen";

	@SubscribeEvent
	public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
		if (event.getLevel().isClientSide())
			return;
		if (!(event.getEntity() instanceof ServerPlayer player))
			return;
		if (!(event.getPlacedBlock().getBlock() instanceof FactoryPanelBlock))
			return;
		if (!CreateCCConfig.FIRST_HINT.get())
			return;

		CompoundTag data = player.getPersistentData();
		if (data.getBoolean(SEEN_FLAG))
			return;
		data.putBoolean(SEEN_FLAG, true);

		player.displayClientMessage(
			Component.translatable("message.create_colored_connections.first_hint"), false);
	}
}
