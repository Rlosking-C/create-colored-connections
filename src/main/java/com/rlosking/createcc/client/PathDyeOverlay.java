package com.rlosking.createcc.client;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The live feedback of a pending chain, modelled on Create's own connection
 * flow ({@code FactoryPanelConnectionHandler.clientTick}): while a chain is
 * selected the gauge a click would confirm is boxed by a blinking outline, and
 * the action bar keeps a one-line hint showing how much the chain covers and
 * which dye it will apply — Create keeps "click second panel" on screen for the
 * whole interaction instead of flashing it once, and reports the concrete
 * reason (in red, with its deny sound) when a target cannot be used.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public final class PathDyeOverlay {

	/** Create's own connection highlight blinks between these two greens. */
	private static final int BOX_COLOR_A = 0x38B764;
	private static final int BOX_COLOR_B = 0xA7F070;

	/** The rejection currently on screen, so its deny sound only plays once. */
	private static String shownRejection;

	private PathDyeOverlay() {}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || mc.level == null)
			return;
		if (!BatchDyeSelection.hasSelection()) {
			shownRejection = null;
			return;
		}

		// A gauge that refused to join explains itself for a moment — and the
		// hint below steps aside so the reason stays readable
		if (BatchDyeSelection.rejectionFresh()) {
			String rejection = BatchDyeSelection.rejection();
			if (!rejection.equals(shownRejection)) {
				shownRejection = rejection;
				AllSoundEvents.DENY.playAt(mc.level, player.blockPosition(), 1, 1, false);
			}
			player.displayClientMessage(Component.translatable(rejection)
				.withStyle(ChatFormatting.RED), true);
			return;
		}
		shownRejection = null;

		// The confirm target — the chain's tail — boxed exactly like Create
		// boxes the panel its connection started from
		FactoryPanelPosition tail = BatchDyeSelection.tail();
		AABB box = tail == null ? null : BatchPreviewRenderer.panelBox(mc.level, tail);
		if (box != null) {
			Outliner.getInstance()
				.showAABB("createcc_path_target", box.inflate(-1.5 / 128f))
				.colored(AnimationTickHolder.getTicks() % 16 > 8 ? BOX_COLOR_A : BOX_COLOR_B)
				.lineWidth(1 / 16f);
		}

		// Just the two gestures, kept on screen until the chain resolves. The
		// line used to carry the link and gauge counts and the dye in hand as
		// well; the vanilla action bar is a single centred line that neither
		// wraps nor steps aside for the screen edge, so anything longer ran off
		// the screen in most languages (English only just fitted, and the
		// gesture hints — the part a player actually needs mid-selection — were
		// the first to go). The chain's size is already readable from the
		// growing preview beams and the boxed target gauge, so the counts cost
		// the line its only real content.
		player.displayClientMessage(Component.translatable("gui.create_colored_connections.path_hint"), true);
	}
}
