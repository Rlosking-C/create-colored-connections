package com.rlosking.createcc.mixin.factorycontroller;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.rlosking.createcc.compat.VirtualConnectionDye;

import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.LogisticsConnection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Connection.class)
public abstract class ConnectionMixin {

	@ModifyReturnValue(method = "fromClient", at = @At("RETURN"))
	private static Connection createcc$readDye(Connection original, RegistryFriendlyByteBuf buf) {
		if (original instanceof LogisticsConnection)
			((VirtualConnectionDye) original).createcc$setDye(buf.readByte() & 0xFF);
		return original;
	}
}
