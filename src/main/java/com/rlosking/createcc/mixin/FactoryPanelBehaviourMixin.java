package com.rlosking.createcc.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.rlosking.createcc.ConnectionColorManager;
import com.rlosking.createcc.ConnectionKey;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;

@Mixin(FactoryPanelBehaviour.class)
public abstract class FactoryPanelBehaviourMixin {

	@Unique
	private FactoryPanelPosition createcc$oldPos;

	@Inject(method = "addConnection", at = @At("TAIL"))
	private void createcc$inheritColor(FactoryPanelPosition fromPos, CallbackInfo ci) {
		FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
		if (!(self.getWorld() instanceof ServerLevel serverLevel))
			return;
		FactoryPanelBehaviour source = FactoryPanelBehaviour.at(serverLevel, fromPos);
		if (source == null || source.targetedBy.isEmpty())
			return;
		DyeColor inherited = null;
		for (FactoryPanelConnection incoming : source.targetedBy.values()) {
			Optional<DyeColor> color =
				ConnectionColorManager.getColor(serverLevel, incoming.from, source.getPanelPosition());
			if (color.isEmpty())
				return;
			if (inherited == null)
				inherited = color.get();
			else if (inherited != color.get())
				return;
		}
		if (inherited != null)
			ConnectionColorManager.setColor(serverLevel, fromPos, self.getPanelPosition(), inherited);
	}

	@Inject(method = "disconnectAll", at = @At("HEAD"))
	private void createcc$clearColors(CallbackInfo ci) {
		FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
		if (!(self.getWorld() instanceof ServerLevel serverLevel))
			return;
		FactoryPanelPosition selfPos = self.getPanelPosition();
		for (FactoryPanelConnection incoming : self.targetedBy.values())
			ConnectionColorManager.clearConnection(serverLevel, incoming.from, selfPos);
		for (FactoryPanelPosition target : self.targeting)
			ConnectionColorManager.clearConnection(serverLevel, selfPos, target);
	}

	@Inject(method = "moveTo", at = @At("HEAD"))
	private void createcc$captureOldPos(FactoryPanelPosition newPos, ServerPlayer player, CallbackInfo ci) {
		this.createcc$oldPos = ((FactoryPanelBehaviour) (Object) this).getPanelPosition();
	}

	@Inject(method = "moveTo", at = @At("TAIL"))
	private void createcc$remapColors(FactoryPanelPosition newPos, ServerPlayer player, CallbackInfo ci) {
		FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
		FactoryPanelPosition oldPos = this.createcc$oldPos;
		this.createcc$oldPos = null;
		if (oldPos == null || oldPos.equals(newPos))
			return;
		if (!(self.getWorld() instanceof ServerLevel serverLevel))
			return;
		for (FactoryPanelPosition target : self.targeting)
			ConnectionColorManager.remap(serverLevel, new ConnectionKey(oldPos, target),
				new ConnectionKey(newPos, target));
		for (FactoryPanelConnection incoming : self.targetedBy.values())
			ConnectionColorManager.remap(serverLevel, new ConnectionKey(incoming.from, oldPos),
				new ConnectionKey(incoming.from, newPos));
	}
}
