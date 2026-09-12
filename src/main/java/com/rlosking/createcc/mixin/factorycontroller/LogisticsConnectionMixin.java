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

/**
 * Carries the dye tag on Factory Controller's virtual ingredient wires.
 *
 * <p>The color is stored on the connection object itself (rather than a
 * parallel map keyed by board coordinates) so FC's own machinery moves it
 * everywhere it needs to go: the controller's world NBT on save, the delta
 * sync bytes to every GUI viewer, and automatic cleanup when the wire is
 * removed or the component it touches is relocated.</p>
 *
 * <p>This mixin is data only — the visual half lives in
 * {@code client.ConnectionWidgetMixin}, which mirrors the world renderer's
 * two looks (idle gray line replaced by the dye, active line keeps its
 * status color and gains a 2px dye border per side) instead of overriding
 * {@code getConnectionColor} with a flat dye color.</p>
 */
@Mixin(LogisticsConnection.class)
public abstract class LogisticsConnectionMixin implements VirtualConnectionDye {

	/** DyeColor ordinal + 1; 0 = not dyed (zero-init safe: white is ordinal 0) */
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

	/**
	 * World save: one byte beside FC's own wire fields. Unknown to older
	 * readers (absent tag reads back as 0 = undyed), so the data format
	 * stays forward-compatible.
	 */
	@ModifyReturnValue(method = "toNBT", at = @At("RETURN"))
	private CompoundTag createcc$saveDye(CompoundTag tag) {
		if (createcc$dye != 0)
			tag.putByte("createcc:dye", (byte) createcc$dye);
		return tag;
	}

	/**
	 * Blueprint payload: {@code BlueprintStorage.build} serializes every wire
	 * through {@code toExportNBT}, which FC implements as
	 * {@code super.toNBT()} plus export-only fields — deliberately bypassing
	 * the {@code toNBT} override above. Without this hook every blueprint
	 * path (schematic import into the library, board save, placement into a
	 * controller) would drop the dye even though the wire object carries it;
	 * the read side already exists in the NBT constructor used by
	 * {@code Connection.fromNBT} on placement.
	 */
	@ModifyReturnValue(method = "toExportNBT", at = @At("RETURN"))
	private CompoundTag createcc$saveDyeExport(CompoundTag tag) {
		if (createcc$dye != 0)
			tag.putByte("createcc:dye", (byte) createcc$dye);
		return tag;
	}

	/** World load: the NBT constructor is the single readback path. */
	@Inject(method = "<init>(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
	private void createcc$loadDye(CompoundTag tag, CallbackInfo ci) {
		// getByte returns 0 for an absent tag — already the "undyed" default
		createcc$dye = tag.getByte("createcc:dye") & 0xFF;
	}

	/**
	 * Client sync write side: one byte appended after FC's own
	 * {@code writeClientExtra} payload. The read side lives in
	 * {@link ConnectionMixin} on the static {@code Connection.fromClient},
	 * which runs after this tail has been produced, so the byte order pairs
	 * up without touching FC's per-type codecs.
	 */
	@Inject(method = "writeClientExtra", at = @At("RETURN"))
	private void createcc$writeDye(RegistryFriendlyByteBuf buf, CallbackInfo ci) {
		buf.writeByte(createcc$dye);
	}
}
