package com.rlosking.createcc.mixin.client;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rlosking.createcc.CreateCCConfig;
import com.rlosking.createcc.client.GogglesTracing;

import com.simibubi.create.content.equipment.goggles.GoggleOverlayRenderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

/**
 * Goggles tracing HUD (author decision): no custom HUD box — the trace
 * summary and status lines are appended straight into Create's own goggle
 * overlay tooltip. That way the trace readout reuses Create's overlay
 * position (the user's overlayOffset config), styling, item icon and its
 * slide/fade-in animation for free, and never overlaps or competes with it.
 *
 * <p>Two injections work together:</p>
 *
 * <p><b>1. {@code isEmpty} guard (ModifyExpressionValue):</b> vanilla aborts
 * the overlay when the looked-at block contributed no tooltip lines — and
 * while tracing, the player most often looks at plain wall blocks that have
 * no goggle information at all. While a trace is active the empty check is
 * forced to "not empty" so the overlay renders with our lines alone. When the
 * trace ends the check returns to vanilla behavior (empty → overlay
 * disappears instantly, same as vanilla).</p>
 *
 * <p><b>2. {@code drawHoveringText} wrap (WrapOperation):</b> right before
 * Create draws the tooltip, the trace lines are appended to the tooltip
 * list. There are two call sites (the plain path and the ModernUI compat
 * path) — they are mutually exclusive per frame, so the wrap handler runs at
 * most once per frame. The list itself is the ArrayList built by
 * {@code renderOverlay}; appending is safe. Width computation happens inside
 * drawHoveringText, so the widened box fits the new lines automatically.</p>
 */
@Mixin(GoggleOverlayRenderer.class)
public class GoggleOverlayRendererMixin {

	/**
	 * The third {@code List.isEmpty()} call inside renderOverlay is the final
	 * "nothing to show" guard before drawing (after the goggle-info and
	 * pole-length branches). While a trace is active, force it to "not empty"
	 * so the overlay stays alive even over blocks without goggle information.
	 */
	@ModifyExpressionValue(method = "renderOverlay",
		at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z", ordinal = 2))
	private static boolean createcc$keepOverlayAliveForTrace(boolean original) {
		if (original && CreateCCConfig.TRACE_HUD.get() && GogglesTracing.shouldShowTooltip())
			return false;
		return original;
	}

	/**
	 * Appends the trace summary / status lines to the overlay tooltip right
	 * before Create renders it.
	 */
	@WrapOperation(method = "renderOverlay",
		at = @At(value = "INVOKE",
			target = "Lcom/simibubi/create/foundation/gui/RemovedGuiUtils;drawHoveringText(Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/List;IIIIIIIILnet/minecraft/client/gui/Font;)V"))
	private static void createcc$appendTraceToGoggleOverlay(GuiGraphics guiGraphics,
		List<? extends FormattedText> lines, int mouseX, int mouseY, int screenWidth, int screenHeight,
		int maxWidth, int bgColor, int borderColorStart, int borderColorEnd, Font font,
		Operation<Void> original) {
		if (CreateCCConfig.TRACE_HUD.get() && GogglesTracing.shouldShowTooltip()) {
			// renderOverlay always builds the tooltip as a fresh ArrayList —
			// appending in place is safe and keeps Create's own lines first
			@SuppressWarnings("unchecked")
			List<Component> mutable = (List<Component>) lines;
			GogglesTracing.appendTooltip(mutable);
		}
		original.call(guiGraphics, lines, mouseX, mouseY, screenWidth, screenHeight, maxWidth, bgColor,
			borderColorStart, borderColorEnd, font);
	}
}
