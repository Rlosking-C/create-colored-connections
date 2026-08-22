package com.colconn.createcc;

import java.util.List;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Connection picking: inverse-transforms the crosshair hit point into the
 * panel's "path space" and finds the shortest distance to each connection
 * polyline.
 *
 * <p>Path space matches the output space of
 * {@code FactoryPanelConnection.calculatePathDiff} (rotation chain:
 * -yRot around Y → -(xRot+90°) around X → 180° around Y, all around the block
 * center). In that space the panel slot lies on the y≈0 plane and connection
 * polyline anchors are 0.5 blocks apart.</p>
 *
 * <p>Usable on both sides: the client predicts and sends the packet, the
 * server re-validates and cancels the vanilla interaction. The client
 * renderer also reuses the same picking result to drive hover lift and
 * overlap detection.</p>
 */
public class ConnectionHitTester {

	/** Hit threshold (blocks): the visual line is roughly 3px wide, keep some tolerance */
	private static final double HIT_THRESHOLD = 0.3;
	/** In-plane search radius: matches Create's maximum link distance of 16 blocks */
	private static final int SEARCH_RADIUS = 16;

	/** Hit result: connection key (from = source panel, to = covered target panel) */
	public record Hit(FactoryPanelPosition from, FactoryPanelPosition to) {}

	/**
	 * Searches for the nearest dyeable connection within the plane of the hit
	 * face (connections only exist on the same surface plane).
	 *
	 * @param level the world (client or server)
	 * @param hit   crosshair hit position in world space
	 * @param face  the hit face's direction (decides the search plane)
	 * @return the hit connection; null when nothing is within the threshold
	 */
	public static Hit find(Level level, Vec3 hit, Direction face) {
		BlockPos base = BlockPos.containing(hit);
		// Hit-face normal axis → the two in-plane iteration directions
		Direction first, second;
		switch (face.getAxis()) {
			case X -> { first = Direction.UP; second = Direction.SOUTH; }
			case Y -> { first = Direction.EAST; second = Direction.SOUTH; }
			default -> { first = Direction.EAST; second = Direction.UP; }
		}
		Hit best = null;
		double bestDist = HIT_THRESHOLD;
		// Search two layers: the block containing the hit, plus one layer along
		// the hit-face normal. When clicking a floating segment, the ray passes
		// through the collision-less line and lands on the wall behind, exactly
		// at the wall boundary (e.g. z=3.0). BlockPos.containing floors that
		// boundary value unpredictably into either the wall layer or the gauge
		// layer due to float precision — searching one layer only drops about
		// half of the clicks. Gauges sit on the outer side of the clicked face,
		// so both layers are covered (the two layers are distinct; no double scan).
		for (BlockPos layerBase : new BlockPos[] { base, base.relative(face) }) {
			for (int i = -SEARCH_RADIUS; i <= SEARCH_RADIUS; i++) {
				for (int j = -SEARCH_RADIUS; j <= SEARCH_RADIUS; j++) {
					BlockPos pos = layerBase.relative(first, i).relative(second, j);
					BlockEntity be = level.getBlockEntity(pos);
					if (!(be instanceof FactoryPanelBlockEntity fpbe))
						continue;
					BlockState state = be.getBlockState();
					for (FactoryPanelBehaviour behaviour : fpbe.panels.values()) {
						if (!behaviour.isActive())
							continue;
						// Only panel→panel connections are dyeable; link lines (targetedByLinks) don't take part
						for (FactoryPanelConnection connection : behaviour.targetedBy.values()) {
							double d = distanceToPath(level, state, behaviour, connection, hit);
							if (d < bestDist) {
								bestDist = d;
								best = new Hit(connection.from, behaviour.getPanelPosition());
							}
						}
					}
				}
			}
		}
		return best;
	}

	/**
	 * Shortest distance from the hit point to one connection's polyline.
	 * Returns positive infinity when a hit is impossible (different face, or
	 * empty path).
	 */
	private static double distanceToPath(Level level, BlockState state, FactoryPanelBehaviour behaviour,
		FactoryPanelConnection connection, Vec3 hit) {
		List<Direction> path = connection.getPath(level, state, behaviour.getPanelPosition());
		if (path.isEmpty())
			return Double.MAX_VALUE;

		// World coordinates → target panel block-local coordinates
		Vec3 local = hit.subtract(Vec3.atLowerCornerOf(behaviour.getPos()));
		float xRotDeg = Mth.RAD_TO_DEG * FactoryPanelBlock.getXRot(state);
		float yRotDeg = Mth.RAD_TO_DEG * FactoryPanelBlock.getYRot(state);
		// Local coordinates → path space: the renderer's rotateCentered rotates
		// around the block center, so the inverse transform subtracts the center
		// first, rotates, then adds it back — restoring the [0,1]³ path
		// coordinate frame (path lines live on the y≈0 plane)
		Vec3 v = local.subtract(0.5, 0.5, 0.5);
		v = VecHelper.rotate(v, -yRotDeg, Axis.Y);
		v = VecHelper.rotate(v, -(xRotDeg + 90), Axis.X);
		v = VecHelper.rotate(v, 180, Axis.Y);
		v = v.add(0.5, 0.5, 0.5);
		// Connections live on the y≈0 plane of path space; a larger deviation
		// means the hit point is on a different face
		if (Math.abs(v.y) > 0.375)
			return Double.MAX_VALUE;

		// Polyline anchors: start at the target panel slot and advance 0.5
		// blocks along each path direction (the first degenerate
		// segment-to-point call = point-to-point distance)
		double px = behaviour.slot.xOffset * 0.5 + 0.25;
		double pz = behaviour.slot.yOffset * 0.5 + 0.25;
		double best = distToSegmentSqr(v.x, v.z, px, pz, px, pz);
		double prevX = px, prevZ = pz;
		for (Direction d : path) {
			double nextX = prevX + d.getStepX() * 0.5;
			double nextZ = prevZ + d.getStepZ() * 0.5;
			best = Math.min(best, distToSegmentSqr(v.x, v.z, prevX, prevZ, nextX, nextZ));
			prevX = nextX;
			prevZ = nextZ;
		}
		return Math.sqrt(best);
	}

	/**
	 * Squared distance from point (px, pz) to segment (ax, az)–(bx, bz).
	 * Standard point-to-segment projection clamped to the segment ends.
	 */
	private static double distToSegmentSqr(double px, double pz, double ax, double az, double bx, double bz) {
		double dx = bx - ax, dz = bz - az;
		double lenSqr = dx * dx + dz * dz;
		double t = lenSqr == 0 ? 0 : ((px - ax) * dx + (pz - az) * dz) / lenSqr;
		t = Math.max(0, Math.min(1, t));
		double cx = ax + dx * t, cz = az + dz * t;
		return (px - cx) * (px - cx) + (pz - cz) * (pz - cz);
	}
}
