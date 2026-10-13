package com.rlosking.createcc.client;

import java.util.List;

import com.rlosking.createcc.ColoredConnections;
import com.rlosking.createcc.ConnectionHitTester;
import com.rlosking.createcc.network.BatchColorConnectionPacket;
import com.rlosking.createcc.network.BatchSelectionModePacket;
import com.rlosking.createcc.network.ColorConnectionPacket;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

@EventBusSubscriber(modid = ColoredConnections.MODID)
public class DyeInteractionHandler {

	private static final Logger LOGGER = LogUtils.getLogger();

	@SubscribeEvent
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		boolean clientSide = event.getLevel().isClientSide();

		{
			boolean pending = clientSide ? BatchDyeSelection.hasSelection()
				: event.getEntity() instanceof ServerPlayer serverPlayer
					&& BatchSelectionModePacket.isPending(serverPlayer);
			if (pending && event.getHitVec() != null) {
				FactoryPanelBehaviour clicked = clickedPanel(event);
				event.setCanceled(true);
				event.setCancellationResult(InteractionResult.SUCCESS);
				if (clientSide)
					resolvePendingClick(event.getEntity(), clicked, heldDye(event.getEntity()),
						event.getEntity().isShiftKeyDown());
				return;
			}
		}

		ItemStack stack = event.getItemStack();
		if (!(stack.getItem() instanceof DyeItem dyeItem))
			return;
		if (event.getHitVec() == null)
			return;

		if (event.getEntity().isShiftKeyDown()) {
			FactoryPanelBehaviour clicked = clickedPanel(event);
			if (clicked != null) {
				event.setCanceled(true);
				event.setCancellationResult(InteractionResult.SUCCESS);
				if (clientSide)
					startPath(event.getEntity(), event.getLevel(), clicked);
			}
			return;
		}

		if (clickedPanel(event) != null)
			return;

		ConnectionHoverTracker.Key hoveredKey =
			clientSide ? ConnectionHoverTracker.hoveredConnection() : null;
		ConnectionHitTester.Hit hit = ConnectionHitTester.find(event.getLevel(),
			event.getHitVec().getLocation(), event.getHitVec().getDirection(),
			hoveredKey == null ? null : hoveredKey.from(),
			hoveredKey == null ? null : hoveredKey.to());
		if (clientSide)
			LOGGER.info("createcc coloring click {} -> {}", event.getHitVec().getLocation(),
				hit == null ? "no connection hit" : hit.from().pos() + " -> " + hit.to().pos());
		if (hit == null)
			return;

		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);
		if (!clientSide)
			return;

		Player player = event.getEntity();
		DyeColor dye = dyeItem.getDyeColor();
		boolean clear = dye == DyeColor.BLACK;
		PacketDistributor.sendToServer(new ColorConnectionPacket(hit.from(), hit.to(), clear ? -1 : dye.ordinal()));
		player.displayClientMessage(Component.translatable(clear
			? "message.create_colored_connections.cleared"
			: "message.create_colored_connections.dyed")
			.withStyle(ChatFormatting.GREEN), true);
	}

	static FactoryPanelBehaviour panelAt(Level level, BlockPos pos, Vec3 hitLocation) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof FactoryPanelBlock))
			return null;
		FactoryPanelPosition slot = new FactoryPanelPosition(pos,
			FactoryPanelBlock.getTargetedSlot(pos, state, hitLocation));
		FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(level, slot);
		return behaviour != null && behaviour.isActive() ? behaviour : null;
	}

	private static FactoryPanelBehaviour clickedPanel(PlayerInteractEvent.RightClickBlock event) {
		return panelAt(event.getLevel(), event.getPos(), event.getHitVec().getLocation());
	}

	private static void startPath(Player player, Level level, FactoryPanelBehaviour clicked) {
		if (BatchDyeSelection.hasSelection())
			return;
		BatchDyeSelection.select(level, clicked.getPanelPosition());
		player.displayClientMessage(Component.translatable("message.create_colored_connections.path_start")
			.withStyle(ChatFormatting.GREEN), true);
	}

	static void resolvePendingClick(Player player, FactoryPanelBehaviour clicked, DyeItem dyeItem, boolean aborts) {
		if (aborts || clicked == null) {
			LOGGER.info("createcc path click -> cancel ({})", aborts ? "shift held" : "no gauge under crosshair");
			cancelPath(player);
			return;
		}
		if (dyeItem == null) {
			LOGGER.info("createcc path click -> cancel (no dye in either hand)");
			BatchDyeSelection.clear();
			player.displayClientMessage(Component.translatable(
				"message.create_colored_connections.path_no_dye"), true);
			return;
		}
		FactoryPanelPosition slot = clicked.getPanelPosition();

		if (slot.equals(BatchDyeSelection.start())) {
			LOGGER.info("createcc path click -> cancel (clicked the start gauge {})", slot.pos());
			cancelPath(player);
			return;
		}

		if (BatchDyeSelection.size() >= 2 && slot.equals(BatchDyeSelection.tail())) {
			List<FactoryPanelPosition> nodes = BatchDyeSelection.nodes();
			DyeColor dye = dyeItem.getDyeColor();
			boolean clear = dye == DyeColor.BLACK;
			BatchDyeSelection.clear();
			PacketDistributor.sendToServer(new BatchColorConnectionPacket(nodes, clear ? -1 : dye.ordinal()));
			LOGGER.info("createcc path click -> confirm {} gauges as {}", nodes.size(), dye);
			player.displayClientMessage(Component.translatable(clear
				? "message.create_colored_connections.path_cleared"
				: "message.create_colored_connections.path_dyed")
				.withStyle(ChatFormatting.GREEN), true);
			return;
		}

		LOGGER.info("createcc path click -> append {}", slot.pos());
		BatchDyeSelection.step(player.level(), slot, true);
	}

	private static void cancelPath(Player player) {
		BatchDyeSelection.clear();
		player.displayClientMessage(Component.translatable("message.create_colored_connections.path_cancel"), true);
	}

	static DyeItem heldDye(Player player) {
		if (player.getMainHandItem().getItem() instanceof DyeItem main)
			return main;
		if (player.getOffhandItem().getItem() instanceof DyeItem off)
			return off;
		return null;
	}
}
