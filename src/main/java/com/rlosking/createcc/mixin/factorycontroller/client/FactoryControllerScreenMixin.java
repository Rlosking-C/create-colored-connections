package com.rlosking.createcc.mixin.factorycontroller.client;

import com.rlosking.createcc.CreateCCConfig;
import com.rlosking.createcc.network.FCColorConnectionPacket;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.blueprint.BlueprintPlacement;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.LogisticsConnection;
import io.github.nbcss.createfactorycontroller.content.gui.screen.controller.FactoryControllerScreen;
import io.github.nbcss.createfactorycontroller.content.gui.screen.controller.states.ConnectionModeState;
import io.github.nbcss.createfactorycontroller.content.gui.widget.ConnectionWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import org.jetbrains.annotations.Nullable;

@Mixin(FactoryControllerScreen.class)
public abstract class FactoryControllerScreenMixin extends AbstractContainerScreen<FactoryControllerMenu> {

	private static final Logger LOGGER = LogManager.getLogger("createcc-fc");

	@Shadow
	@Nullable
	private ConnectionWidget hoveredConn;

	@Shadow
	@Nullable
	private BlueprintPlacement pendingPlacement;

	@Shadow
	@Nullable
	private EditBox nameBox;

	@Shadow
	@Nullable
	private VirtualComponentPosition pendingRelocateTarget;

	@Shadow
	@Final
	private ConnectionModeState connectionMode;

	@Shadow
	protected abstract boolean isInCanvasArea(double x, double y);

	protected FactoryControllerScreenMixin(FactoryControllerMenu menu,
			net.minecraft.world.entity.player.Inventory inventory, net.minecraft.network.chat.Component title) {
		super(menu, inventory, title);
	}

	@Redirect(method = "renderBoard",
		at = @At(value = "INVOKE",
			target = "Lio/github/nbcss/createfactorycontroller/content/block/FactoryControllerMenu;getCarried()Lnet/minecraft/world/item/ItemStack;",
			ordinal = 0))
	private ItemStack createcc$carriedDyeKeepsHover(FactoryControllerMenu menu) {
		ItemStack carried = menu.getCarried();
		return carried.getItem() instanceof DyeItem ? ItemStack.EMPTY : carried;
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void createcc$dyeWireClick(double mouseX, double mouseY, int button,
			CallbackInfoReturnable<Boolean> cir) {
		if (button != 1 || hasShiftDown())
			return;
		if (!CreateCCConfig.FACTORY_CONTROLLER_DYEING.get()) {
			LOGGER.info("[createcc-fc] click skipped: feature disabled by config");
			return;
		}
		ConnectionWidget hovered = this.hoveredConn;
		if (hovered == null || !(hovered.connection instanceof LogisticsConnection)) {
			LOGGER.info("[createcc-fc] click skipped: hoveredConn={}, type={}",
				hovered == null ? "null" : hovered.connection.type.name());
			return;
		}
		if (this.pendingPlacement != null || this.connectionMode.isActive() || this.pendingRelocateTarget != null) {
			LOGGER.info("[createcc-fc] click skipped: modal active (placement={}, connMode={}, relocate={})",
				this.pendingPlacement != null, this.connectionMode.isActive(), this.pendingRelocateTarget != null);
			return;
		}
		if (this.nameBox != null && this.nameBox.isFocused()) {
			LOGGER.info("[createcc-fc] click skipped: name box focused");
			return;
		}
		if (!isInCanvasArea(mouseX, mouseY)) {
			LOGGER.info("[createcc-fc] click skipped: outside canvas");
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null)
			return;
		DyeColor color = null;
		if (this.menu.getCarried().getItem() instanceof DyeItem carriedDye)
			color = carriedDye.getDyeColor();
		else if (player.getMainHandItem().getItem() instanceof DyeItem handDye)
			color = handDye.getDyeColor();
		if (color == null) {
			LOGGER.info("[createcc-fc] click skipped: cursor holds {}, main hand holds {}",
				this.menu.getCarried().getItem(), player.getMainHandItem().getItem());
			return;
		}
		int ordinal = color == DyeColor.BLACK ? -1 : color.ordinal();
		Connection conn = hovered.connection;
		LOGGER.info("[createcc-fc] sending dye packet: {} from=({},{}) to=({},{}) controller={}",
			color, conn.from.x(), conn.from.y(), conn.to.x(), conn.to.y(), menu.controllerPos);
		PacketDistributor.sendToServer(new FCColorConnectionPacket(menu.controllerPos,
			conn.from.x(), conn.from.y(), conn.to.x(), conn.to.y(), conn.type.name(), ordinal));
		cir.setReturnValue(true);
	}
}
