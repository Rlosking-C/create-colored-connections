package com.rlosking.createcc;

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

public class ConnectionHitTester {

	private static final double HIT_THRESHOLD = 0.3;
	private static final double SWITCH_MARGIN = 0.15;
	private static final int SEARCH_RADIUS = 16;

	public record Hit(FactoryPanelPosition from, FactoryPanelPosition to, double dist) {}

	public static Hit find(Level level, Vec3 hit, Direction face) {
		return find(level, hit, face, null, null);
	}

	public static Hit find(Level level, Vec3 hit, Direction face,
			FactoryPanelPosition stickyFrom, FactoryPanelPosition stickyTo) {
		BlockPos base = BlockPos.containing(hit);
		Direction first, second;
		switch (face.getAxis()) {
			case X -> { first = Direction.UP; second = Direction.SOUTH; }
			case Y -> { first = Direction.EAST; second = Direction.SOUTH; }
			default -> { first = Direction.EAST; second = Direction.UP; }
		}
		Hit best = null;
		double bestDist = HIT_THRESHOLD;
		Hit stickyHit = null;
		double stickyDist = Double.MAX_VALUE;
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
						for (FactoryPanelConnection connection : behaviour.targetedBy.values()) {
							double d = distanceToPath(level, state, behaviour, connection, hit);
							if (d < bestDist) {
								bestDist = d;
								best = new Hit(connection.from, behaviour.getPanelPosition(), d);
							}
							if (stickyFrom != null && stickyTo != null
								&& stickyFrom.equals(connection.from)
								&& stickyTo.equals(behaviour.getPanelPosition())
								&& d < stickyDist) {
								stickyDist = d;
								stickyHit = new Hit(connection.from, behaviour.getPanelPosition(), d);
							}
						}
					}
				}
			}
		}
		if (stickyHit != null && stickyDist <= HIT_THRESHOLD
				&& bestDist > stickyDist - SWITCH_MARGIN)
			return stickyHit;
		return best;
	}

	private static double distanceToPath(Level level, BlockState state, FactoryPanelBehaviour behaviour,
		FactoryPanelConnection connection, Vec3 hit) {
		List<Direction> path = connection.getPath(level, state, behaviour.getPanelPosition());
		if (path.isEmpty())
			return Double.MAX_VALUE;

		Vec3 local = hit.subtract(Vec3.atLowerCornerOf(behaviour.getPos()));
		float xRotDeg = Mth.RAD_TO_DEG * FactoryPanelBlock.getXRot(state);
		float yRotDeg = Mth.RAD_TO_DEG * FactoryPanelBlock.getYRot(state);
		Vec3 v = local.subtract(0.5, 0.5, 0.5);
		v = VecHelper.rotate(v, -yRotDeg, Axis.Y);
		v = VecHelper.rotate(v, -(xRotDeg + 90), Axis.X);
		v = VecHelper.rotate(v, 180, Axis.Y);
		v = v.add(0.5, 0.5, 0.5);
		if (Math.abs(v.y) > 0.375)
			return Double.MAX_VALUE;

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

	private static double distToSegmentSqr(double px, double pz, double ax, double az, double bx, double bz) {
		double dx = bx - ax, dz = bz - az;
		double lenSqr = dx * dx + dz * dz;
		double t = lenSqr == 0 ? 0 : ((px - ax) * dx + (pz - az) * dz) / lenSqr;
		t = Math.max(0, Math.min(1, t));
		double cx = ax + dx * t, cz = az + dz * t;
		return (px - cx) * (px - cx) + (pz - cz) * (pz - cz);
	}
}
