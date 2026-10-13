package com.rlosking.createcc.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.rlosking.createcc.ColoredConnections;
import com.rlosking.createcc.ConnectionKey;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

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

@EventBusSubscriber(modid = ColoredConnections.MODID, value = Dist.CLIENT)
public final class BatchPreviewRenderer {

	private static final float BEAM_WIDTH = 0.0625F;
	private static final double HALF_BEAM = BEAM_WIDTH / 2;
	private static final int BEAM_COLOR = 0x95CD41;
	private static final double FLOAT_HEIGHT = 0.15;
	private static final double GAUGE_REACH = HALF_BEAM;

	private BatchPreviewRenderer() {}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		if (!BatchDyeSelection.hasSelection())
			return;
		Level level = Minecraft.getInstance().level;
		if (level == null)
			return;
		List<Piece> pieces = new ArrayList<>();
		for (ConnectionKey key : BatchDyeSelection.previewPath())
			collectConnectionBeams(level, key, pieces);
		pieces.sort(Comparator.comparing(Piece::key));
		drawWithoutOverlap(pieces);
	}

	private static void collectConnectionBeams(Level level, ConnectionKey key, List<Piece> pieces) {
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

		String keyPrefix = "createcc_path_" + key.from().pos().asLong() + "_" + key.from().slot().ordinal()
			+ "_" + key.to().pos().asLong() + "_" + key.to().slot().ordinal();

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

		List<Run> drawn = new ArrayList<>();
		for (Run run : runs) {
			if (run.start().distanceToSqr(run.end()) >= 1.0E-6)
				drawn.add(run);
		}

		for (int i = 0; i < drawn.size(); i++) {
			Run run = drawn.get(i);
			Vec3 dir = run.end().subtract(run.start()).normalize();
			double reachStart = i == 0 ? -GAUGE_REACH : HALF_BEAM;
			double reachEnd = i == drawn.size() - 1 ? GAUGE_REACH : 0;
			Vec3 lo = run.start().add(dir.scale(reachStart));
			Vec3 hi = run.end().add(dir.scale(reachEnd));
			if (hi.subtract(lo).dot(dir) > 1.0E-6)
				pieces.add(new Piece(keyPrefix + "_" + i,
					crossExpandedBox(lo, hi, worldAxisOf(dir), HALF_BEAM), dir));
			if (i > 0) {
				Run previous = drawn.get(i - 1);
				Vec3 incoming = previous.end().subtract(previous.start()).normalize();
				pieces.add(new Piece(keyPrefix + "_c" + i,
					cornerBox(run.start(), incoming, dir), null));
			}
		}
	}

	private record Piece(String key, AABB box, Vec3 axis) {}

	private static void drawWithoutOverlap(List<Piece> pieces) {
		List<Piece> placed = new ArrayList<>();
		for (Piece piece : pieces) {
			List<AABB> visible = uncovered(piece, placed);
			for (int i = 0; i < visible.size(); i++) {
				Outliner.getInstance()
					.showOutline(visible.size() == 1 ? piece.key() : piece.key() + "_" + i,
						new WorldAlignedBeamOutline(visible.get(i), BEAM_WIDTH))
					.colored(BEAM_COLOR)
					.lineWidth(BEAM_WIDTH);
			}
			placed.add(piece);
		}
	}

	private static List<AABB> uncovered(Piece piece, List<Piece> placed) {
		AABB box = piece.box();
		if (piece.axis() == null)
			return List.of(box);
		Axis axis = worldAxisOf(piece.axis());
		double lo = axisMin(box, axis);
		double hi = axisMax(box, axis);

		List<double[]> cuts = new ArrayList<>();
		for (Piece other : placed) {
			if (!coversCrossSection(other.box(), box, axis))
				continue;
			double from = Math.max(lo, axisMin(other.box(), axis));
			double to = Math.min(hi, axisMax(other.box(), axis));
			if (to - from > 1.0E-6)
				cuts.add(new double[] { from, to });
		}
		if (cuts.isEmpty())
			return List.of(box);

		cuts.sort(Comparator.comparingDouble(cut -> cut[0]));
		List<AABB> parts = new ArrayList<>();
		double cursor = lo;
		for (double[] cut : cuts) {
			if (cut[0] - cursor > 1.0E-6)
				parts.add(slice(box, axis, cursor, cut[0]));
			cursor = Math.max(cursor, cut[1]);
		}
		if (hi - cursor > 1.0E-6)
			parts.add(slice(box, axis, cursor, hi));
		return parts;
	}

	private static boolean coversCrossSection(AABB other, AABB box, Axis axis) {
		double eps = 1.0E-6;
		if (axis != Axis.X && (other.minX > box.minX + eps || other.maxX < box.maxX - eps))
			return false;
		if (axis != Axis.Y && (other.minY > box.minY + eps || other.maxY < box.maxY - eps))
			return false;
		if (axis != Axis.Z && (other.minZ > box.minZ + eps || other.maxZ < box.maxZ - eps))
			return false;
		return true;
	}

	private static double axisMin(AABB box, Axis axis) {
		return axis == Axis.X ? box.minX : axis == Axis.Y ? box.minY : box.minZ;
	}

	private static double axisMax(AABB box, Axis axis) {
		return axis == Axis.X ? box.maxX : axis == Axis.Y ? box.maxY : box.maxZ;
	}

	private static AABB slice(AABB box, Axis axis, double from, double to) {
		if (axis == Axis.X)
			return new AABB(from, box.minY, box.minZ, to, box.maxY, box.maxZ);
		if (axis == Axis.Y)
			return new AABB(box.minX, from, box.minZ, box.maxX, to, box.maxZ);
		return new AABB(box.minX, box.minY, from, box.maxX, box.maxY, to);
	}

	private record Run(Vec3 start, Vec3 end) {}

	private static AABB cornerBox(Vec3 vertex, Vec3 incoming, Vec3 outgoing) {
		Vec3 lo = vertex.add(outgoing.scale(-HALF_BEAM));
		Vec3 hi = vertex.add(incoming.scale(HALF_BEAM)).add(outgoing.scale(HALF_BEAM));
		Axis normal = worldAxisOf(incoming.cross(outgoing));
		double ex = normal == Axis.X ? HALF_BEAM : 0;
		double ey = normal == Axis.Y ? HALF_BEAM : 0;
		double ez = normal == Axis.Z ? HALF_BEAM : 0;
		return new AABB(Math.min(lo.x, hi.x) - ex, Math.min(lo.y, hi.y) - ey, Math.min(lo.z, hi.z) - ez,
			Math.max(lo.x, hi.x) + ex, Math.max(lo.y, hi.y) + ey, Math.max(lo.z, hi.z) + ez);
	}

	private static Axis worldAxisOf(Vec3 dir) {
		double ax = Math.abs(dir.x);
		double ay = Math.abs(dir.y);
		double az = Math.abs(dir.z);
		if (ax >= ay && ax >= az)
			return Axis.X;
		return ay >= az ? Axis.Y : Axis.Z;
	}

	private static AABB crossExpandedBox(Vec3 lo, Vec3 hi, Axis axis, double half) {
		double ex = axis == Axis.X ? 0 : half;
		double ey = axis == Axis.Y ? 0 : half;
		double ez = axis == Axis.Z ? 0 : half;
		return new AABB(Math.min(lo.x, hi.x) - ex, Math.min(lo.y, hi.y) - ey, Math.min(lo.z, hi.z) - ez,
			Math.max(lo.x, hi.x) + ex, Math.max(lo.y, hi.y) + ey, Math.max(lo.z, hi.z) + ez);
	}

	private static Vec3 toWorld(BlockPos origin, float xRotDeg, float yRotDeg, double pathX, double pathZ) {
		return toWorld(origin, xRotDeg, yRotDeg, pathX, FLOAT_HEIGHT, pathZ);
	}

	private static Vec3 toWorld(BlockPos origin, float xRotDeg, float yRotDeg, double pathX, double pathY,
		double pathZ) {
		Vec3 v = new Vec3(pathX, pathY, pathZ);
		v = VecHelper.rotateCentered(v, 180, Axis.Y);
		v = VecHelper.rotateCentered(v, xRotDeg + 90, Axis.X);
		v = VecHelper.rotateCentered(v, yRotDeg, Axis.Y);
		return v.add(Vec3.atLowerCornerOf(origin));
	}

	static AABB panelBox(Level level, FactoryPanelPosition pos) {
		FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(level, pos);
		if (behaviour == null || !behaviour.isActive())
			return null;
		BlockState state = behaviour.blockEntity.getBlockState();
		float xRotDeg = Mth.RAD_TO_DEG * FactoryPanelBlock.getXRot(state);
		float yRotDeg = Mth.RAD_TO_DEG * FactoryPanelBlock.getYRot(state);
		BlockPos origin = behaviour.getPos();

		double pathX = behaviour.slot.xOffset * 0.5 + 0.25;
		double pathZ = behaviour.slot.yOffset * 0.5 + 0.25;
		Vec3 center = toWorld(origin, xRotDeg, yRotDeg, pathX, 0, pathZ);
		Vec3 normal = toWorld(BlockPos.ZERO, xRotDeg, yRotDeg, 0, 1, 0)
			.subtract(toWorld(BlockPos.ZERO, xRotDeg, yRotDeg, 0, 0, 0));
		double nx = Math.abs(normal.x);
		double ny = Math.abs(normal.y);
		double nz = Math.abs(normal.z);
		return new AABB(center, center).inflate(nx < 0.5 ? 3 / 16.0 : 0, ny < 0.5 ? 3 / 16.0 : 0,
			nz < 0.5 ? 3 / 16.0 : 0);
	}
}
