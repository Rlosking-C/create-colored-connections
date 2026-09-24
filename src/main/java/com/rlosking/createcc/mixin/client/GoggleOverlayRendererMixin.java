package com.rlosking.createcc.mixin.client;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rlosking.createcc.CreateCCConfig;
import com.rlosking.createcc.client.GogglesTracing;

import com.simibubi.create.content.equipment.goggles.GoggleOverlayRenderer;

import net.minecraft.network.chat.Component;

/**
 * Goggles tracing HUD (author decision): no custom HUD box — the trace
 * summary and status lines are appended straight into Create's own goggle
 * overlay tooltip. That way the trace readout reuses Create's overlay
 * position (the user's overlayOffset config), styling, item icon and its
 * slide/fade-in animation for free, and never overlaps or competes with it.
 *
 * <p><b>Where the lines are appended matters.</b> {@code renderOverlay}
 * measures the tooltip first (max line width, then the panel height) and only
 * then derives its anchor from that measurement:</p>
 *
 * <pre>posX = min(width / 2 + overlayOffsetX, width - tooltipTextWidth - 20)</pre>
 *
 * <p>Appending at the draw call, as an earlier revision did, left that
 * measurement blind to the trace lines. Create's clamp then produced an
 * anchor that was fine for its own lines only, {@code RemovedGuiUtils}
 * found the real tooltip wider than the space left of the screen edge, and
 * flipped the whole panel to the <i>left</i> of the anchor
 * ({@code tooltipX = posX - 16 - tooltipTextWidth}) — while the item icon
 * stayed where it was drawn, at {@code posX + 10}, on the right. Russian and
 * German, the widest of the shipped languages, crossed that limit first and
 * showed a readout detached from its icon; English and Chinese sat just
 * inside it. When even the flipped panel did not fit, vanilla degraded
 * further and wrapped mid-sentence with the continuation flush against the
 * panel's left edge, which is what the wrapped German summary line was.</p>
 *
 * <p>So the lines are appended at the last {@code isEmpty()} guard instead:
 * after every branch that can still add to, drop from or clear the tooltip
 * list has run (including the {@code remove(size() - 1)} that pairs a goggle
 * block's own lines with its hover lines), and before Create measures
 * anything. Width, height, the anchor clamp, the icon position and the
 * fade-in then all see the same list, and the panel can no longer flip away
 * from its icon. The same guard is the "nothing to show" check, which is
 * also why a trace keeps the overlay alive over plain blocks that contribute
 * no goggle information at all: while tracing, the guard reports non-empty.</p>
 */
@Mixin(GoggleOverlayRenderer.class)
public class GoggleOverlayRendererMixin {

	/**
	 * The third {@code List.isEmpty()} call inside renderOverlay is the final
	 * "nothing to show" guard before drawing, and the last point at which the
	 * tooltip list can still be extended safely: the goggle-info and
	 * pole-length branches are behind us, and the width/height measurement,
	 * the anchor clamp and the item icon are all ahead. Appending here both
	 * keeps the overlay alive while a trace is active (the guard is forced to
	 * "not empty") and lets Create size and place the panel around the trace
	 * lines instead of discovering them at the draw call.
	 */
	@WrapOperation(method = "renderOverlay",
		at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z", ordinal = 2))
	private static boolean createcc$appendTraceLinesBeforeMeasure(List<Component> tooltip, Operation<Boolean> original) {
		boolean empty = original.call(tooltip);
		if (!CreateCCConfig.TRACE_HUD.get() || !GogglesTracing.shouldShowTooltip())
			return empty;
		// renderOverlay always builds the tooltip as a fresh ArrayList —
		// appending in place is safe and keeps Create's own lines first
		GogglesTracing.appendTooltip(tooltip);
		return false;
	}
}
