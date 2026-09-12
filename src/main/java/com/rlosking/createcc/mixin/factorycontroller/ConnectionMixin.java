package com.rlosking.createcc.mixin.factorycontroller;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.rlosking.createcc.compat.VirtualConnectionDye;

import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.LogisticsConnection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Client sync read side for GUI wire dyeing.
 *
 * <p>FC's delta sync serializes each changed wire as captured
 * {@code writeClient} bytes and decodes them with the static
 * {@code Connection.fromClient} — the single funnel every wire type flows
 * through on the client. By reading the dye byte <i>after</i> that method
 * returns (its buffer is then positioned exactly past FC's own payload,
 * where {@code LogisticsConnectionMixin} appended ours), every sync path —
 * menu open, delta upsert, full resync — picks the color up without a
 * per-subclass hook. Only ingredient wires carry the extra byte, matching
 * the write side's instanceof guard, so the buffer never desyncs.</p>
 */
@Mixin(Connection.class)
public abstract class ConnectionMixin {

	@ModifyReturnValue(method = "fromClient", at = @At("RETURN"))
	private static Connection createcc$readDye(Connection original, RegistryFriendlyByteBuf buf) {
		// Non-ingredient wire types never appended a dye byte; skip them so
		// the buffer position stays consistent for any trailing data
		if (original instanceof LogisticsConnection)
			((VirtualConnectionDye) original).createcc$setDye(buf.readByte() & 0xFF);
		return original;
	}
}
