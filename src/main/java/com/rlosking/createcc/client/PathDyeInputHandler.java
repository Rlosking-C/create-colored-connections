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

/**
 * Owns the use key while a dye chain is pending — the same interception
 * Create's own connection flow performs from its {@code InputEvents}
 * ({@code FactoryPanelConnectionHandler.onRightClick}).
 *
 * <p>Two reasons this has to happen at the input layer instead of in the
 * block-interaction event the addon used to rely on:</p>
 *
 * <p><b>1. It has to be early.</b> Cancelling the interaction event is not
 * enough on its own: the client has already committed the click by then, so
 * the gauge's config screen could still open on the server side. Cancelling
 * the use key stops the interaction before anything vanilla happens — no
 * block use, no packet, no screen. Clicks that land on no block at all (thin
 * air) take the same path, which is what makes a chain abortable without
 * aiming at a block.</p>
 *
 * <p><b>2. It has to be exclusive.</b> Should a screen be requested while the
 * chain is pending anyway — another mod, a late packet, a click that slipped
 * past both handlers — the screen event below refuses it: the chain owns the
 * interaction until it is confirmed or cancelled.</p>
 */
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

		// What the crosshair rests on decides the step: a gauge appends or
		// confirms, anything else cancels
		FactoryPanelBehaviour clicked = null;
		HitResult hit = mc.hitResult;
		if (hit != null && hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit)
			clicked = DyeInteractionHandler.panelAt(mc.level, blockHit.getBlockPos(), blockHit.getLocation());
		DyeInteractionHandler.resolvePendingClick(player, clicked, DyeInteractionHandler.heldDye(player),
			player.isShiftKeyDown());
	}

	/**
	 * The backstop for reason 2 above: while a chain is pending, Create's
	 * gauge screen is not allowed to open at all. The screen advertises the
	 * very action the chain blocks, and it opens exactly where the player is
	 * about to click to confirm.
	 */
	@SubscribeEvent
	public static void onScreenOpening(ScreenEvent.Opening event) {
		if (!(event.getNewScreen() instanceof FactoryPanelScreen))
			return;
		if (BatchDyeSelection.hasSelection())
			event.setCanceled(true);
	}
}
