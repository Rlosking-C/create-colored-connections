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

/**
 * GUI dyeing interaction in the Factory Controller board screen.
 *
 * <p>The player picks a dye up from the inventory onto the cursor (plain
 * left-click, vanilla behavior), moves it over an ingredient wire and
 * right-clicks to dye it — the same "pick up, then apply" vocabulary as
 * every other GUI interaction. A dye held in the main hand also works,
 * matching world dyeing. Black dye clears, nothing is consumed unless
 * configured.</p>
 *
 * <p>Two hooks make this possible: Factory Controller suppresses wire
 * hover entirely while the cursor carries an item, so the first hook
 * (renderBoard redirect) lets a carried <em>dye</em> keep the hover alive —
 * the wire still highlights under the cursor, which is the aiming feedback.
 * The second hook (mouseClicked) sends the dye packet for a plain
 * right-click on the hovered wire, after checking every mode FC itself
 * owns: shift+click stays FC's wire removal, and blueprint placement,
 * wire editing, component relocation and the name field keep their clicks.
 * A plain right-click on a wire has no FC behavior, so consuming it loses
 * nothing, and the dye stays on the cursor for repeat dyeing.</p>
 */
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

	/**
	 * Keeps wire hover alive while the cursor carries a dye.
	 *
	 * <p>FC's renderBoard computes {@code carrying = !menu.getCarried().isEmpty()}
	 * and skips all connection hover while anything is carried (the cursor is
	 * busy holding an item). A dye is the aiming tool for right-click dyeing,
	 * so this redirect reports an empty carried stack for dyes only — the wire
	 * under the cursor highlights and {@code hoveredConn} resolves normally.
	 * Every other carried item, and every other reader of the real carried
	 * stack, is unaffected. The invoke target is FactoryControllerMenu (the
	 * receiver's static type, as javac emits it), matching the second
	 * renderBoard call by ordinal 0 — the first is the {@code carrying} check.</p>
	 */
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
		// Plain right click only (same gesture as world dyeing); shift+click is FC's wire removal
		if (button != 1 || hasShiftDown())
			return;
		if (!CreateCCConfig.FACTORY_CONTROLLER_DYEING.get()) {
			LOGGER.info("[createcc-fc] click skipped: feature disabled by config");
			return;
		}
		ConnectionWidget hovered = this.hoveredConn;
		// Only ingredient wires are dyeable (redstone/number wires excluded)
		if (hovered == null || !(hovered.connection instanceof LogisticsConnection)) {
			LOGGER.info("[createcc-fc] click skipped: hoveredConn={}, type={}",
				hovered == null ? "null" : hovered.connection.type.name());
			return;
		}
		// Never steal clicks from FC's own modal interactions
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
		// Dye source, in GUI-first order: a dye picked up onto the cursor is
		// the intended flow; a main-hand dye still works like world dyeing.
		// Black dye clears. The dye stays where it is (cursor or hand) — the
		// server may charge it only under the dyeConsumption config.
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
		// No optimistic client feedback: the server's delta sync recolors the
		// wire for every viewer, and a rejected dye simply never appears
		cir.setReturnValue(true);
	}
}
