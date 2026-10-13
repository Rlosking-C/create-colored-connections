package com.rlosking.createcc.mixin.factorycontroller;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.rlosking.createcc.compat.VirtualConnectionDye;

import io.github.nbcss.createfactorycontroller.content.component.connection.LogisticsConnection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LogisticsConnection.class)
public abstract class LogisticsConnectionMixin implements VirtualConnectionDye {

	@Unique
	private int createcc$dye = 0;

	@Override
	@Unique
	public int createcc$getDye() {
		return createcc$dye;
	}

	@Override
	@Unique
	public void createcc$setDye(int dye) {
		createcc$dye = dye;
	}

	@ModifyReturnValue(method = "toNBT", at = @At("RETURN"))
	private CompoundTag createcc$saveDye(CompoundTag tag) {
		if (createcc$dye != 0)
			tag.putByte("createcc:dye", (byte) createcc$dye);
		return tag;
	}

	@ModifyReturnValue(method = "toExportNBT", at = @At("RETURN"))
	private CompoundTag createcc$saveDyeExport(CompoundTag tag) {
		if (createcc$dye != 0)
			tag.putByte("createcc:dye", (byte) createcc$dye);
		return tag;
	}

	@Inject(method = "<init>(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
	private void createcc$loadDye(CompoundTag tag, CallbackInfo ci) {
		createcc$dye = tag.getByte("createcc:dye") & 0xFF;
	}

	@Inject(method = "writeClientExtra", at = @At("RETURN"))
	private void createcc$writeDye(RegistryFriendlyByteBuf buf, CallbackInfo ci) {
		buf.writeByte(createcc$dye);
	}
}
