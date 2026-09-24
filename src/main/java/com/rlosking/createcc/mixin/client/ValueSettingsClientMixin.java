package com.rlosking.createcc.mixin.client;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.rlosking.createcc.client.BatchDyeSelection;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsClient;

import net.minecraft.network.chat.MutableComponent;

/**
 * Silences Create's value-box hover tip while a dye chain is pending.
 *
 * <p>On a factory gauge that tip reads "click to configure" — an action the
 * chain deliberately blocks — and it is drawn right over the gauge the player
 * is about to click in order to confirm the path, so instead of a hint the
 * player gets a misleading prompt competing with the action bar line. Create
 * hands the tip to the renderer from {@code FilteringRenderer.renderOnBlock}
 * the moment the crosshair rests on a gauge, so the only place it can be
 * filtered out is here, where it is handed over.</p>
 */
@Mixin(ValueSettingsClient.class)
public class ValueSettingsClientMixin {

	@Inject(method = "showHoverTip", at = @At("HEAD"), cancellable = true)
	private void createcc$hideDuringPathDyeing(List<MutableComponent> tip, CallbackInfo ci) {
		if (BatchDyeSelection.hasSelection())
			ci.cancel();
	}
}
