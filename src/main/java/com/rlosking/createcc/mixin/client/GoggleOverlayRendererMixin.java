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

@Mixin(GoggleOverlayRenderer.class)
public class GoggleOverlayRendererMixin {

	@WrapOperation(method = "renderOverlay",
		at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z", ordinal = 2))
	private static boolean createcc$appendTraceLinesBeforeMeasure(List<Component> tooltip, Operation<Boolean> original) {
		boolean empty = original.call(tooltip);
		if (!CreateCCConfig.TRACE_HUD.get() || !GogglesTracing.shouldShowTooltip())
			return empty;
		GogglesTracing.appendTooltip(tooltip);
		return false;
	}
}
