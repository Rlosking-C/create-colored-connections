package com.rlosking.createcc.mixin.client;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.rlosking.createcc.client.BatchDyeSelection;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsClient;

import net.minecraft.network.chat.MutableComponent;

@Mixin(ValueSettingsClient.class)
public class ValueSettingsClientMixin {

	@Inject(method = "showHoverTip", at = @At("HEAD"), cancellable = true)
	private void createcc$hideDuringPathDyeing(List<MutableComponent> tip, CallbackInfo ci) {
		if (BatchDyeSelection.hasSelection())
			ci.cancel();
	}
}
