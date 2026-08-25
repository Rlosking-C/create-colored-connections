package com.rlosking.createcc.client;

import java.util.ArrayList;
import java.util.List;

import com.rlosking.createcc.ColoredConnections;
import com.rlosking.createcc.ConnectionKey;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;

import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Path-dyeing preview: solid green light beams floating just off the surface
 * along every link of the path that would be dyed, drawn while a batch
 * selection is pending.
 *
 * <p>Beams are world-aligned cuboids drawn through Catnip's outline pipeline
 * in Create's valid-preview green {@code 0x95CD41}. Unlike
 * {@code Outliner.showLine} (whose beam-aligned pose darkens vertical runs —
 * see {@link WorldAlignedBeamOutline}), the cuboids here all face the shader
 * with a world-up normal, so every run glows with the same intensity.</p>
 *
 * <p>Geometry: each connection's polyline is walked in the panel's "path
 * space" (the y≈0 plane the connection lines are drawn on, x/z advancing
 * half a block per path step — the same model {@code ConnectionHitTester}
 * uses for hit picking), offset 0.15 blocks along path-space +Y (the
 * surface's outward normal, away from the wall/floor/ceiling the gauges sit
 * on), and mapped back to world coordinates with the forward counterpart of
 * the hit tester's inverse rotation chain. Collinear half-block steps merge
 * into one straight run per beam.</p>
 *
 * <p>Joints: where two runs meet at an L-corner, BOTH runs extend half a
 * beam width past the corner, so their cuboids overlap and completely fill
 * the corner instead of leaving the classic notched gap. At the path's ends
 * (the two gauges) each beam reaches 0.25 blocks into the gauge quadrant so
 * it visually plugs into the gauge instead of stopping short of it. Because
 * every face renders with the same color, normal and light, these overlaps
 * are seamless.</p>
 *
 * <p>Outliner entries expire after one tick, so the beams are refreshed
 * every client tick while the selection is pending and fade out within a few
 * ticks once the path resolves, expires, or the player confirms — the dye
 * colors appear as the preview disappears.</p>
 */
@EventBusSubscriber(modid = ColoredConnections.MODID, value = Dist.CLIENT)
public final class BatchPreviewRenderer {

	/** Beam thickness in blocks — the value Create's track preview uses for its start/end rails */
	private static final float BEAM_WIDTH = 0.125F;
	/** Half the beam thickness; the corner overlap and cross-section radius */
	private static final double HALF_BEAM = BEAM_WIDTH / 2;
	/** Create's valid-preview green (the same color track and chain-conveyor previews show) */
	private static final int BEAM_COLOR = 0x95CD41;
	/** Beam hover distance off the surface, in path-space Y (= the surface's outward normal) */
	private static final double FLOAT_HEIGHT = 0.15;
	/** How far the first/last beam reach into their gauge quadrant, so beams plug into the gauges */
	private static final double GAUGE_REACH = 0.25;

	private BatchPreviewRenderer() {}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		if (!BatchDyeSelection.hasSelection())
			return;
		Level level = Minecraft.getInstance().level;
		if (level == null)
			return;
		for (ConnectionKey key : BatchDyeSelection.previewPath())
			renderConnectionBeams(level, key);
	}

	/**
	 * Draws the floating beams for one connection of the previewed path. The
	 * connection object is looked up on the target panel (Create stores links
	 * in the target's {@code targetedBy}); its path is walked from the
	 * target's slot anchor exactly like the hit tester does.
	 */
	private static void renderConnectionBeams(Level level, ConnectionKey key) {
		FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(level, key.to());
		if (behaviour == null || !behaviour.isActive())
			return;
		FactoryPanelConnection connection = behaviour.targetedBy.get(key.from());
		if (connection == null)
			return;
		BlockState state = behaviour.blockEntity.getBlockState();
		List<Direction> path = connection.getPath(level, state, behaviour.getPanelPosition());
		if (path.isEmpty())
			return;

		float xRotDeg = Mth.RAD_TO_DEG * FactoryPanelBlock.getXRot(state);
		float yRotDeg = Mth.RAD_TO_DEG * FactoryPanelBlock.getYRot(state);
		BlockPos origin = behaviour.getPos();

		// Stable outline keys (pos + slot of both ends): a HashSet's iteration
		// order changes between resamples, so index-based keys would flicker —
		// identity-based keys keep unchanged beams alive across resamples
		String keyPrefix = "createcc_path_" + key.from().pos().asLong() + "_" + key.from().slot().ordinal()
			+ "_" + key.to().pos().asLong() + "_" + key.to().slot().ordinal();

		// Walk the polyline in path space; collinear steps merge into straight runs
		double px = behaviour.slot.xOffset * 0.5 + 0.25;
		double pz = behaviour.slot.yOffset * 0.5 + 0.25;
		Vec3 runStart = toWorld(origin, xRotDeg, yRotDeg, px, pz);
		Vec3 runEnd = runStart;
		Direction runDir = null;
		List<Run> runs = new ArrayList<>();
		for (Direction d : path) {
			px += d.getStepX() * 0.5;
			pz += d.getStepZ() * 0.5;
			Vec3 vertex = toWorld(origin, xRotDeg, yRotDeg, px, pz);
			if (runDir == null) {
				runDir = d;
			} else if (runDir.getAxis() != d.getAxis()) {
				runs.add(new Run(runStart, runEnd));
				runStart = runEnd;
				runDir = d;
			}
			runEnd = vertex;
		}
		runs.add(new Run(runStart, runEnd));

		for (int i = 0; i < runs.size(); i++) {
			Run run = runs.get(i);
			if (run.start().distanceToSqr(run.end()) < 1.0E-6)
				continue;
			// Ends touching another run overlap it by half a beam width (a
			// seamless corner fill); the very first/last ends reach into
			// their gauge instead
			double extStart = i == 0 ? GAUGE_REACH : HALF_BEAM;
			double extEnd = i == runs.size() - 1 ? GAUGE_REACH : HALF_BEAM;
			Vec3 dir = run.end().subtract(run.start()).normalize();
			Vec3 lo = run.start().subtract(dir.scale(extStart));
			Vec3 hi = run.end().add(dir.scale(extEnd));
			AABB box = crossExpandedBox(lo, hi, worldAxisOf(dir), HALF_BEAM);
			Outliner.getInstance()
				.showOutline(keyPrefix + "_" + i, new WorldAlignedBeamOutline(box, BEAM_WIDTH))
				.colored(BEAM_COLOR)
				.lineWidth(BEAM_WIDTH);
		}
	}

	/** One straight piece of the previewed polyline, in world coordinates. */
	private record Run(Vec3 start, Vec3 end) {}

	/** The world axis a run travels along (runs are axis-aligned by construction). */
	private static Axis worldAxisOf(Vec3 dir) {
		double ax = Math.abs(dir.x);
		double ay = Math.abs(dir.y);
		double az = Math.abs(dir.z);
		if (ax >= ay && ax >= az)
			return Axis.X;
		return ay >= az ? Axis.Y : Axis.Z;
	}

	/**
	 * The run's bounding box, thickened by {@code half} on the two axes
	 * perpendicular to its direction — the run axis itself already spans the
	 * beam's length (including the joint extensions).
	 */
	private static AABB crossExpandedBox(Vec3 lo, Vec3 hi, Axis axis, double half) {
		double ex = axis == Axis.X ? 0 : half;
		double ey = axis == Axis.Y ? 0 : half;
		double ez = axis == Axis.Z ? 0 : half;
		return new AABB(Math.min(lo.x, hi.x) - ex, Math.min(lo.y, hi.y) - ey, Math.min(lo.z, hi.z) - ez,
			Math.max(lo.x, hi.x) + ex, Math.max(lo.y, hi.y) + ey, Math.max(lo.z, hi.z) + ez);
	}

	/**
	 * Path space → world: the forward counterpart of the hit tester's
	 * inverse chain (and the same chain Create's own
	 * {@code FactoryPanelBlock.getTargetedSlot} uses) — rotate 180° around Y,
	 * then xRot+90° around X, then yRot around Y, all around the block
	 * center, then offset by the block's world position.
	 */
	private static Vec3 toWorld(BlockPos origin, float xRotDeg, float yRotDeg, double pathX, double pathZ) {
		Vec3 v = new Vec3(pathX, FLOAT_HEIGHT, pathZ);
		v = VecHelper.rotateCentered(v, 180, Axis.Y);
		v = VecHelper.rotateCentered(v, xRotDeg + 90, Axis.X);
		v = VecHelper.rotateCentered(v, yRotDeg, Axis.Y);
		return v.add(Vec3.atLowerCornerOf(origin));
	}
}
