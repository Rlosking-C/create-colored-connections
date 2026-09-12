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

/**
 * Carries world-side dye colors into blueprint imports
 * ({@code Create Schematic & Quill selection -> Factory Controller board}).
 *
 * <p>FC's {@code SchematicImport.scan} walks every selected wall gauge and,
 * for each incoming ingredient link ({@code targetedBy}), creates the matching
 * virtual {@code LogisticsConnection} between the two gauges' board cells.
 * That new wire carries no dye, so an imported board always renders plain —
 * even when the wall links it mirrors are dyed. This mixin fills the gap:
 * when the host gauge is visited, its world {@link FactoryPanelPosition} is
 * remembered; when each virtual wire is added, the world link's dye is looked
 * up in the client mirror (same {@link ConnectionColorManager} key semantics:
 * from = source panel of {@code targetedBy}, to = host panel) and stamped
 * onto the wire. From there every later hop — GUI preview, board sync
 * (writeClientExtra), blueprint save (toExportNBT) and placement (fromNBT) —
 * already transports the dye through the existing mixins.</p>
 *
 * <p>Only ingredient wires are dyed; the redstone-link lambda
 * ({@code lambda$scan$1}) is left untouched, mirroring the world-side rule
 * that redstone/display links are never colored.</p>
 */
@Mixin(SchematicImport.class)
public abstract class SchematicImportMixin {

	/**
	 * World position of the gauge whose {@code targetedBy} map is currently
	 * being walked. {@code scan} is client-side and single-threaded, so a
	 * thread-local carries it from the loop into the per-connection lambda —
	 * the lambda only receives board coordinates and cannot see its host's
	 * world position.
	 */
	@Unique
	private static final ThreadLocal<FactoryPanelPosition> createcc$host = new ThreadLocal<>();

	/**
	 * Intercepts the {@code behaviour.targetedBy} read inside scan's gauge
	 * loop (the only such read in the method; redstone links use the separate
	 * {@code targetedByLinks} field) and records the host gauge's world
	 * position for the connection lambda below.
	 *
	 * <p>Static because {@code scan} itself is static — a non-static callback
	 * cannot be injected into a static target method.</p>
	 */
	@Redirect(method = "scan", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
			target = "Lcom/simibubi/create/content/logistics/factoryBoard/FactoryPanelBehaviour;targetedBy:Ljava/util/Map;"))
	private static Map<FactoryPanelPosition, FactoryPanelConnection> createcc$captureHost(FactoryPanelBehaviour behaviour) {
		createcc$host.set(behaviour.getPanelPosition());
		return behaviour.targetedBy;
	}

	/**
	 * After the imported virtual wire has been added to the board graph,
	 * fetches it back (both endpoints are board cells derivable from the
	 * lambda's arguments), looks up the world link's dye and stamps it.
	 */
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
		// scan() runs on the client (it reads the Schematic & Quill selection),
		// so the client mirror of the color table is the correct lookup source.
		ConnectionColorManager.getColor(Minecraft.getInstance().level, fromPos, hostPos)
				.ifPresent(dye -> dyeable.createcc$setDye(dye.ordinal() + 1));
	}
}
