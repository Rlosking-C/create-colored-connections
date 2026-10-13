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

@EventBusSubscriber(value = Dist.CLIENT)
public final class PathDyeOverlay {

	private static final int BOX_COLOR_A = 0x38B764;
	private static final int BOX_COLOR_B = 0xA7F070;

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

		FactoryPanelPosition tail = BatchDyeSelection.tail();
		AABB box = tail == null ? null : BatchPreviewRenderer.panelBox(mc.level, tail);
		if (box != null) {
			Outliner.getInstance()
				.showAABB("createcc_path_target", box.inflate(-1.5 / 128f))
				.colored(AnimationTickHolder.getTicks() % 16 > 8 ? BOX_COLOR_A : BOX_COLOR_B)
				.lineWidth(1 / 16f);
		}

		player.displayClientMessage(Component.translatable("gui.create_colored_connections.path_hint"), true);
	}
}
