package com.rlosking.createcc.client;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(value = Dist.CLIENT)
public final class PathDyeInputHandler {

	private PathDyeInputHandler() {}

	@SubscribeEvent
	public static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
		if (!event.isUseItem())
			return;
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || mc.level == null || !BatchDyeSelection.hasSelection())
			return;

		event.setCanceled(true);
		event.setSwingHand(true);

		FactoryPanelBehaviour clicked = null;
		HitResult hit = mc.hitResult;
		if (hit != null && hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit)
			clicked = DyeInteractionHandler.panelAt(mc.level, blockHit.getBlockPos(), blockHit.getLocation());
		DyeInteractionHandler.resolvePendingClick(player, clicked, DyeInteractionHandler.heldDye(player),
			player.isShiftKeyDown());
	}

	@SubscribeEvent
	public static void onScreenOpening(ScreenEvent.Opening event) {
		if (!(event.getNewScreen() instanceof FactoryPanelScreen))
			return;
		if (BatchDyeSelection.hasSelection())
			event.setCanceled(true);
	}
}
