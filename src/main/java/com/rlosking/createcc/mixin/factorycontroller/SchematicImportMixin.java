package com.rlosking.createcc.mixin.factorycontroller;

import java.util.Map;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.rlosking.createcc.ConnectionColorManager;
import com.rlosking.createcc.compat.VirtualConnectionDye;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

import io.github.nbcss.createfactorycontroller.content.blueprint.SchematicImport;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionGraph;
import io.github.nbcss.createfactorycontroller.content.component.connection.LogisticsConnection;
import net.minecraft.client.Minecraft;

@Mixin(SchematicImport.class)
public abstract class SchematicImportMixin {

	@Unique
	private static final ThreadLocal<FactoryPanelPosition> createcc$host = new ThreadLocal<>();

	@Redirect(method = "scan", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
			target = "Lcom/simibubi/create/content/logistics/factoryBoard/FactoryPanelBehaviour;targetedBy:Ljava/util/Map;"))
	private static Map<FactoryPanelPosition, FactoryPanelConnection> createcc$captureHost(FactoryPanelBehaviour behaviour) {
		createcc$host.set(behaviour.getPanelPosition());
		return behaviour.targetedBy;
	}

	@Inject(method = "lambda$scan$0", at = @At(value = "INVOKE",
			target = "Lio/github/nbcss/createfactorycontroller/content/component/connection/ConnectionGraph;add(Lio/github/nbcss/createfactorycontroller/content/component/connection/Connection;)V",
			shift = At.Shift.AFTER))
	private static void createcc$carryDye(Map<FactoryPanelPosition, VirtualComponentPosition> panelToPos,
			VirtualComponentPosition pos, ConnectionGraph graph, FactoryPanelPosition fromPos,
			FactoryPanelConnection panelConn, CallbackInfo ci) {
		FactoryPanelPosition hostPos = createcc$host.get();
		if (hostPos == null)
			return;
		VirtualComponentPosition fromBoard = panelToPos.get(fromPos);
		if (fromBoard == null)
			return;
		Connection wire = graph.get(fromBoard, pos, LogisticsConnection.TYPE);
		if (!(wire instanceof VirtualConnectionDye dyeable))
			return;
		ConnectionColorManager.getColor(Minecraft.getInstance().level, fromPos, hostPos)
				.ifPresent(dye -> dyeable.createcc$setDye(dye.ordinal() + 1));
	}
}
