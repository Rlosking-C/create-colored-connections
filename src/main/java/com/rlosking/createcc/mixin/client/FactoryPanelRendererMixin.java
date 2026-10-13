package com.rlosking.createcc.mixin.client;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.rlosking.createcc.ConnectionColorManager;
import com.rlosking.createcc.CreateCCConfig;
import com.rlosking.createcc.client.ConnectionHoverTracker;
import com.rlosking.createcc.client.GogglesTracing;
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
import net.createmod.catnip.theme.Color;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(FactoryPanelRenderer.class)
public abstract class FactoryPanelRendererMixin {

	private static final int TRACE_GRAY = 0xFF606060;

	@Inject(method = "renderPath", at = @At("HEAD"), cancellable = true)
	private static void createcc$renderDyedPath(FactoryPanelBehaviour behaviour, FactoryPanelConnection connection,
		float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay, CallbackInfo ci) {
		float hoverLift = CreateCCConfig.HOVER_LIFT.get()
			? ConnectionHoverTracker.liftFor(connection.from, behaviour.getPanelPosition())
			: 0;
		ConnectionHoverTracker.stashedLift = hoverLift;

		DyeColor dye = ConnectionColorManager.getColor(behaviour.getWorld(), connection.from,
			behaviour.getPanelPosition()).orElse(null);
		if (dye == null)
			return;

		BlockState blockState = behaviour.blockEntity.getBlockState();
		List<Direction> path = connection.getPath(behaviour.getWorld(), blockState, behaviour.getPanelPosition());
		if (path.isEmpty())
			return;

		FactoryPanelSupportBehaviour sbe = FactoryPanelBehaviour.linkAt(behaviour.getWorld(), connection);
		if (sbe != null && (sbe.blockEntity instanceof RedstoneLinkBlockEntity
			|| sbe.blockEntity instanceof DisplayLinkBlockEntity))
			return;

		float traceGray = GogglesTracing.grayStrengthFor(connection.from, behaviour.getPanelPosition());

		float glow = behaviour.bulb.getValue(partialTicks);
		boolean flashing = !behaviour.redstonePowered && !behaviour.waitingForNetwork && glow > 0
			&& !behaviour.satisfied;

		boolean idle = behaviour.count == 0 && !behaviour.isMissingAddress()
			&& !behaviour.redstonePowered && !flashing && !behaviour.waitingForNetwork;
		boolean extraGauge = behaviour.getClass().getName().startsWith("net.liukrast.eg.");
		boolean cover = idle && !extraGauge;

		float yOffset = 1;
		yOffset += behaviour.promisedSatisfied ? 1 : behaviour.satisfied ? 0 : 2;
		if (!behaviour.redstonePowered && !behaviour.waitingForNetwork && glow > 0 && !behaviour.satisfied
			&& !behaviour.promisedSatisfied) {
			float p = (1 - (1 - glow) * (1 - glow));
			yOffset += (connection.success ? 1 : 2) * p;
		}
		yOffset += cover ? 0 : -0.1875f;

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
			currentX += direction.getStepX() * .5;
			currentZ += direction.getStepZ() * .5;

			Direction modelDir = direction.getOpposite();
			boolean alongX = modelDir.getAxis() == Direction.Axis.X;
			float yLayer = yOffset + (direction.get2DDataValue() % 2) * 0.125f + hoverLift;

			float band = cover ? 1 : 2;

			if (i == 0 && !cover) {
				createcc$underlay(AllPartialModels.FACTORY_PANEL_LINES.get(modelDir), blockState, alongX, 2,
					xRot, yRot, anchorX, anchorZ, currentX, currentZ, yLayer, scroll, dyeColor, traceGray, ms, buffer, light, overlay);
				createcc$underlay(AllPartialModels.FACTORY_PANEL_ARROWS.get(modelDir), blockState, alongX, 1.5f,
					xRot, yRot, anchorX, anchorZ, currentX, currentZ, yLayer, scroll, dyeColor, traceGray, ms, buffer, light, overlay);
			} else {
				PartialModel partial = (i == 0 ? AllPartialModels.FACTORY_PANEL_ARROWS
					: AllPartialModels.FACTORY_PANEL_LINES).get(modelDir);
				createcc$underlay(partial, blockState, alongX, band,
					xRot, yRot, anchorX, anchorZ, currentX, currentZ, yLayer, scroll, dyeColor, traceGray, ms,
					buffer, light, overlay);
			}
		if (cover)
			ci.cancel();

		}

	}

	private static void createcc$underlay(PartialModel partial, BlockState blockState, boolean alongX,
		float widthScale, float xRot, float yRot, float anchorX, float anchorZ, float x, float z,
		float yLayer, boolean scroll, int dyeColor, float traceGray, PoseStack ms, MultiBufferSource buffer,
		int light, int overlay) {
		SuperByteBuffer sprite = CachedBuffers.partial(partial, blockState)
			.rotateCentered(yRot, Direction.UP)
			.rotateCentered(xRot, Direction.EAST)
			.rotateCentered(Mth.PI, Direction.UP)
			.translate(anchorX, 0, anchorZ)
			.translate(x, yLayer / 512f, z);
		if (widthScale != 1) {
			if (alongX)
				sprite.scale(1, 1, widthScale);
			else
				sprite.scale(widthScale, 1, 1);
		}
		if (scroll)
			sprite.shiftUV(AllSpriteShifts.FACTORY_PANEL_CONNECTIONS);
		sprite.color(traceGray > 0 ? Color.mixColors(dyeColor, TRACE_GRAY, traceGray) : dyeColor)
			.light(light)
			.overlay(overlay)
			.renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
	}

	@Redirect(method = "renderPath", at = @At(value = "INVOKE",
		target = "Lnet/createmod/catnip/render/SuperByteBuffer;color(I)Lnet/createmod/catnip/render/SuperByteBuffer;"))
	private static SuperByteBuffer createcc$traceDimVanillaLine(SuperByteBuffer sprite, int color,
		FactoryPanelBehaviour behaviour, FactoryPanelConnection connection, float partialTicks, PoseStack ms,
		MultiBufferSource buffer, int light, int overlay) {
		float traceGray = GogglesTracing.grayStrengthFor(connection.from, behaviour.getPanelPosition());
		if (traceGray <= 0)
			return sprite.color(color);
		return sprite.color(Color.mixColors(color, TRACE_GRAY, traceGray));
	}

	@ModifyArg(method = "renderPath", at = @At(value = "INVOKE",
		target = "Lnet/createmod/catnip/render/SuperByteBuffer;translate(FFF)Ldev/engine_room/flywheel/lib/transform/Translate;",
		ordinal = 0), index = 1)
	private static float createcc$hoverLiftBody(float y) {
		return y + ConnectionHoverTracker.stashedLift / 512f;
	}
}
