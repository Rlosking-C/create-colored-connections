package com.rlosking.createcc.mixin.client;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.rlosking.createcc.ConnectionColorManager;
import com.rlosking.createcc.client.ConnectionHoverTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.AllSpriteShifts;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelRenderer;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSupportBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Client rendering injection: two coloring looks depending on panel state
 * (the design validated during Phase 2).
 *
 * <p><b>Idle state (count == 0, vanilla gray line)</b>: the vanilla render is
 * cancelled and the whole line is replaced with the dye color — gray carries
 * no information, so here the dye color is the line's only semantics.</p>
 *
 * <p><b>Active state (in progress / satisfied / promised / waiting for
 * network / redstone-aborted / restock flashing)</b>: a widened dye underlay
 * is rendered below the vanilla line, then the vanilla render proceeds and
 * its core stacks on top, leaving a 1px dye border on each side — the status
 * color (including its flashing animation) is fully preserved while the dye
 * acts as an identification border; no color mixing. Widths are derived from
 * the actual texture geometry: the straight-line texture has a 2px opaque
 * core → 2× scale (4px, 1px peeking per side); the arrow segment combines a
 * 2px shaft with 4px barbs whose vanilla widths differ, so no single scale
 * fits both — instead two same-colored underlays are rendered: the line
 * model at 2× covering the full segment (shaft gets 1px per side, matching
 * the body) plus the arrow model at 1.5× (barbs 6px, 1px per side). The
 * underlay sinks only 0.0625/512 instead of 1/512: vanilla separates
 * crossing lines with a 0.125/512 axis offset (east-west segments always sit
 * above north-south ones), and sinking too deep would let a crossing line's
 * core punch through the border and break that layering.</p>
 *
 * <p>Link lines (redstone / display) carry status semantics in their color,
 * are never dyed, and are left entirely to vanilla.</p>
 *
 * <p>Colors come from {@link DyeColor#getTextureDiffuseColor()} (wool
 * palette) rather than {@code getTextColor()} (sign palette): the latter is
 * high-saturation neon that clashes with Create's desaturated industrial
 * look; the wool palette is MC's standard dyed-block range, the same family
 * Create's own dyed blocks use.</p>
 */
@Mixin(FactoryPanelRenderer.class)
public abstract class FactoryPanelRendererMixin {

	@Inject(method = "renderPath", at = @At("HEAD"), cancellable = true)
	private static void createcc$renderDyedPath(FactoryPanelBehaviour behaviour, FactoryPanelConnection connection,
		float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay, CallbackInfo ci) {
		// Hover-lift progress: must be computed and stashed before any early
		// return (uncolored / link line / empty path) — the vanilla core render
		// follows immediately, and its Y-argument injection point (the
		// ModifyArg below) only receives the float argument, relying on this
		// stash to recover the current connection's lift (renderPath calls are
		// serialized per connection on the render thread; the stash is safe)
		float hoverLift = ConnectionHoverTracker.liftFor(connection.from, behaviour.getPanelPosition());
		ConnectionHoverTracker.stashedLift = hoverLift;

		DyeColor dye = ConnectionColorManager.getColor(behaviour.getWorld(), connection.from,
			behaviour.getPanelPosition()).orElse(null);
		if (dye == null)
			return;

		BlockState blockState = behaviour.blockEntity.getBlockState();
		List<Direction> path = connection.getPath(behaviour.getWorld(), blockState, behaviour.getPanelPosition());
		if (path.isEmpty())
			return;

		// Link lines (redstone / display) carry status semantics in their color — never dyed, fully vanilla
		FactoryPanelSupportBehaviour sbe = FactoryPanelBehaviour.linkAt(behaviour.getWorld(), connection);
		if (sbe != null && (sbe.blockEntity instanceof RedstoneLinkBlockEntity
			|| sbe.blockEntity instanceof DisplayLinkBlockEntity))
			return;

		float glow = behaviour.bulb.getValue(partialTicks);
		// Restock flashing (vanilla condition copied): the gray line briefly
		// flashes white/orange to signal success or failure — a valid feedback
		boolean flashing = !behaviour.redstonePowered && !behaviour.waitingForNetwork && glow > 0
			&& !behaviour.satisfied;
		// Idle: no demand (count==0), no config warning, not aborted, no
		// flashing feedback, not waiting for network (while waiting, vanilla
		// shows an amber line as a valid status cue, so it's not idle) —
		// the gray line carries no information
		boolean idle = behaviour.count == 0 && !behaviour.isMissingAddress() && !behaviour.redstonePowered
			&& !flashing && !behaviour.waitingForNetwork;

		// Vanilla yOffset layering copied verbatim: satisfied 1 / promised 2 /
		// in progress 3, rising up to 5 while flashing
		float yOffset = 1;
		yOffset += behaviour.promisedSatisfied ? 1 : behaviour.satisfied ? 0 : 2;
		if (!behaviour.redstonePowered && !behaviour.waitingForNetwork && glow > 0 && !behaviour.satisfied
			&& !behaviour.promisedSatisfied) {
			float p = (1 - (1 - glow) * (1 - glow));
			yOffset += (connection.success ? 1 : 2) * p;
		}
		// Idle full-line sits on the same layer as the vanilla gray line (the
		// vanilla render is cancelled, no raise needed); the active-state
		// underlay sinks by only 0.0625: deep enough to hide under its own
		// core (leaving 1px borders on both sides), yet shallow enough to stay
		// above the core of a same-state line on the crossing axis (vanilla's
		// axis offset is just 0.125) — otherwise a crossing vertical line's
		// core would punch through the horizontal line's dye border and the
		// layering would look inconsistent
		yOffset += idle ? 0 : -0.0625f;

		int dyeColor = 0xFF000000 | dye.getTextureDiffuseColor();
		boolean scroll = !behaviour.isMissingAddress() && !behaviour.waitingForNetwork
			&& !behaviour.satisfied && !behaviour.redstonePowered;

		float xRot = FactoryPanelBlock.getXRot(blockState) + Mth.PI / 2;
		float yRot = FactoryPanelBlock.getYRot(blockState);
		float anchorX = behaviour.slot.xOffset * .5f + .25f;
		float anchorZ = behaviour.slot.yOffset * .5f + .25f;
		float currentX = 0;
		float currentZ = 0;

		for (int i = 0; i < path.size(); i++) {
			Direction direction = path.get(i);
			// Vanilla logic: advance the anchor to the segment's end before
			// rendering it (the model quad extends 0.5 blocks backward from
			// the anchor, exactly covering this segment)
			currentX += direction.getStepX() * .5;
			currentZ += direction.getStepZ() * .5;

			Direction modelDir = direction.getOpposite();
			boolean alongX = modelDir.getAxis() == Direction.Axis.X;
			// Vanilla axis offset: east-west segments sit 0.125/512 above
			// north-south ones — crossings always resolve as "horizontal
			// above, vertical below"
			float yLayer = yOffset + (direction.get2DDataValue() % 2) * 0.125f + hoverLift;

			if (i == 0 && !idle) {
				// Arrow segment, dual underlay: shaft (2px) and barbs (4px)
				// have different vanilla widths, so no single scale fits —
				// A. line model at 2× covering the whole segment: shaft →4px,
				//    1px peeking per side (matches the body) and the dye
				//    border stays continuous along the entire link
				// B. arrow model at 1.5×: barbs 4px→6px, 1px per side
				//    (2× would peek 2px)
				// Two same-color layers stacked: the shaft takes A's 4px, the
				// barbs take B's 6px — exactly 1px of border everywhere
				createcc$underlay(AllPartialModels.FACTORY_PANEL_LINES.get(modelDir), blockState, alongX, 2, 1,
					xRot, yRot, anchorX, anchorZ, currentX, currentZ, yLayer, scroll, dyeColor, ms, buffer, light, overlay);
				createcc$underlay(AllPartialModels.FACTORY_PANEL_ARROWS.get(modelDir), blockState, alongX, 1.5f, 1,
					xRot, yRot, anchorX, anchorZ, currentX, currentZ, yLayer, scroll, dyeColor, ms, buffer, light, overlay);
			} else {
				// Straight segment at 2× width (2px→4px, 1px peeking per side);
				// the idle full-line keeps its original width
				PartialModel partial = (i == 0 ? AllPartialModels.FACTORY_PANEL_ARROWS
					: AllPartialModels.FACTORY_PANEL_LINES).get(modelDir);
				createcc$underlay(partial, blockState, alongX, idle ? 1 : 2, 1,
					xRot, yRot, anchorX, anchorZ, currentX, currentZ, yLayer, scroll, dyeColor, ms, buffer, light, overlay);
			}
		}

		// Idle: the vanilla gray line is replaced by the full dye line;
		// active states proceed, the vanilla core stacks on the underlay
		if (idle)
			ci.cancel();
	}

	/**
	 * Renders one dye underlay: builds the same transform chain as vanilla,
	 * scales along the width axis (and optionally the length axis), colors
	 * and outputs it.
	 *
	 * @param alongX      whether the model's length axis is X (the width
	 *                    scale is applied to the other axis)
	 * @param widthScale  width-axis scale (2 = 1px border per side; 1 = original width)
	 * @param lengthScale length-axis scale (1 = no crop; see SuperByteBuffer's
	 *                    scale-then-translate composition: the quad locally
	 *                    covers [-0.5, 0] along the length axis, extending
	 *                    from the anchor toward the segment start, so scaling
	 *                    crops from the segment-start end)
	 */
	private static void createcc$underlay(PartialModel partial, BlockState blockState, boolean alongX,
		float widthScale, float lengthScale, float xRot, float yRot, float anchorX, float anchorZ, float x, float z,
		float yLayer, boolean scroll, int dyeColor, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
		SuperByteBuffer sprite = CachedBuffers.partial(partial, blockState)
			.rotateCentered(yRot, Direction.UP)
			.rotateCentered(xRot, Direction.EAST)
			.rotateCentered(Mth.PI, Direction.UP)
			.translate(anchorX, 0, anchorZ)
			.translate(x, yLayer / 512f, z);
		if (widthScale != 1 || lengthScale != 1) {
			if (alongX)
				sprite.scale(lengthScale, 1, widthScale);
			else
				sprite.scale(widthScale, 1, lengthScale);
		}
		// Same scrolling-texture condition as vanilla (washboard animation);
		// the dye color scrolls along
		if (scroll)
			sprite.shiftUV(AllSpriteShifts.FACTORY_PANEL_CONNECTIONS);
		sprite.color(dyeColor)
			.light(light)
			.overlay(overlay)
			.renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
	}

	/**
	 * Hover lift, vanilla-core half: renderPath passes each segment's final
	 * height as the Y argument of the per-segment translate (the FFF
	 * overload, the only call site of that overload in the method); this
	 * appends the lift — it reads the same animation state as the dye border
	 * drawn by the HEAD injection above, so border and core rise together
	 * with unchanged relative layering, and the whole line (dyed or not)
	 * floats above every other line. The lift is measured in layer units
	 * (each layer = 1/512 blocks): zero visible displacement, it only
	 * changes the crossing occlusion order.
	 *
	 * <p>The handler receives only the float argument being modified (Mixin
	 * forbids host-method parameters at this injection point); the current
	 * connection's lift travels via
	 * {@link ConnectionHoverTracker#stashedLift}, written by the HEAD
	 * injection at the top of this method.</p>
	 */
	@ModifyArg(method = "renderPath", at = @At(value = "INVOKE",
		target = "Lnet/createmod/catnip/render/SuperByteBuffer;translate(FFF)Ldev/engine_room/flywheel/lib/transform/Translate;",
		ordinal = 0), index = 1)
	private static float createcc$hoverLiftBody(float y) {
		return y + ConnectionHoverTracker.stashedLift / 512f;
	}
}
