package com.rlosking.createcc.mixin.factorycontroller.client;

import com.rlosking.createcc.compat.DyeUnderlay;
import com.rlosking.createcc.compat.VirtualConnectionDye;

import io.github.nbcss.createfactorycontroller.content.block.ComponentHolder;
import io.github.nbcss.createfactorycontroller.content.component.gauge.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.render.VirtualConnectionRenderer;
import io.github.nbcss.createfactorycontroller.content.gui.widget.ConnectionWidget;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import org.joml.Vector2i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(ConnectionWidget.class)
public abstract class ConnectionWidgetMixin {

	@Unique
	private static final float createcc$FLASH_DECAY = 8f;

	@Unique
	private static ConnectionWidget createcc$hovered;

	@Shadow
	@Final
	public Connection connection;

	@Shadow
	@Final
	List<Vector2i> path;

	@Shadow
	public abstract void renderHighlight(GuiGraphics gfx);

	@Inject(method = "renderHighlight", at = @At("HEAD"))
	private void createcc$markHovered(GuiGraphics gfx, CallbackInfo ci) {
		createcc$hovered = (ConnectionWidget) (Object) this;
	}

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void createcc$dyedWire(GuiGraphics gfx, ComponentHolder holder, CallbackInfo ci) {
		if (!(connection instanceof VirtualConnectionDye dyeable) || dyeable.createcc$getDye() == 0)
			return;
		boolean hovered = createcc$hovered == (Object) this;
		if (hovered)
			createcc$hovered = null;
		int dye = dyeable.createcc$getDye();
		int dyeColor = 0xFF000000 | DyeColor.byId(dye - 1).getTextureDiffuseColor();

		ClientLevel level = Minecraft.getInstance().level;
		long animationTick = connection.getFlashTick(holder, level != null ? level.getGameTime() : -1L);
		boolean flashing = false;
		if (animationTick >= 0) {
			float age = animationTick + AnimationTickHolder.getPartialTicks();
			float glow = Mth.clamp(1f - age / createcc$FLASH_DECAY, 0f, 1f);
			flashing = glow > 0f;
		}

		if (holder.componentAt(connection.to) instanceof VirtualGaugeBehaviour behaviour
				&& (!behaviour.isActive() || behaviour.isMissingAddress() || behaviour.isRedstonePaused())
				&& !flashing) {
			VirtualConnectionRenderer.create(path, dyeColor, animationTick >= 0)
				.drawPath(gfx.bufferSource(), gfx.pose());
			ci.cancel();
			return;
		}

		DyeUnderlay.draw(gfx, path, dyeColor, animationTick >= 0);

		if (hovered) {
			renderHighlight(gfx);
			createcc$hovered = null;
		}
	}

	@Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true)
	private void createcc$dyeTooltipLine(ComponentHolder holder, int count, int index, boolean locked,
			CallbackInfoReturnable<List<FormattedCharSequence>> cir) {
		if (!(connection instanceof VirtualConnectionDye dyeable) || dyeable.createcc$getDye() == 0)
			return;
		List<FormattedCharSequence> lines = cir.getReturnValue();
		if (lines.isEmpty())
			return;
		DyeColor dye = DyeColor.byId(dyeable.createcc$getDye() - 1);
		Component dyeName = Component.translatable("item.minecraft." + dye.getName() + "_dye")
				.withStyle(Style.EMPTY.withColor(dye.getTextColor()));
		List<FormattedCharSequence> withDye = new ArrayList<>(lines);
		withDye.add(1, Component.translatable("gui.create_colored_connections.wire_dye", dyeName)
				.withStyle(ChatFormatting.GRAY).getVisualOrderText());
		cir.setReturnValue(withDye);
	}
}
