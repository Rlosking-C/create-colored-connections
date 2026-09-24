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
 * <p>Joints: runs butt end to end, and each L-corner gets a matching corner
 * piece that fills exactly the quadrant the two beams leave open (see
 * {@link #cornerBox}). At the path's ends (the two gauges) each beam reaches
 * 0.25 blocks into the gauge quadrant so it visually plugs into the gauge
 * instead of stopping short of it.</p>
 *
 * <p>No two cuboids share space: the pieces are drawn in a stable order and
 * every piece is cut back to what the earlier ones have not already painted
 * (see {@link #drawWithoutOverlap}). Overlapping beams double-blend into
 * darker patches and z-fight where their faces are coincident, which is what
 * a crossing of the chain used to look like.</p>
 *
 * <p>Outliner entries expire after one tick, so the beams are refreshed
 * every client tick while the selection is pending and fade out within a few
 * ticks once the path resolves, expires, or the player confirms — the dye
 * colors appear as the preview disappears.</p>
 */
@EventBusSubscriber(modid = ColoredConnections.MODID, value = Dist.CLIENT)
public final class BatchPreviewRenderer {

	/** Beam thickness in blocks — 1px, half of Create's track-preview rail thickness */
	private static final float BEAM_WIDTH = 0.0625F;
	/** Half the beam thickness; the corner overlap and cross-section radius */
	private static final double HALF_BEAM = BEAM_WIDTH / 2;
	/** Create's valid-preview green (the same color track and chain-conveyor previews show) */
	private static final int BEAM_COLOR = 0x95CD41;
	/** Beam hover distance off the surface, in path-space Y (= the surface's outward normal) */
	private static final double FLOAT_HEIGHT = 0.15;
	/**
	 * How far the first/last beam reaches past its gauge's slot centre — half a
	 * beam width, so the two links meeting at a gauge still join inside it
	 * without either sticking out of the far side.
	 *
	 * <p>It used to be 0.25 blocks, which made the approach and the departure
	 * of every gauge the path turns at poke a quarter block beyond the slot.
	 * Where the path turns at a gauge, the two links' stubs then pointed in
	 * opposite directions and the elbow read as a "+" — four arms instead of
	 * two. Halved to the same overlap the corner fills use, the union of the
	 * two links stays an elbow: each arm ends exactly where the connecting
	 * beam's cross-section already covers the joint.</p>
	 */
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
		// Stable order matters: whichever piece comes first is the one later
		// pieces are cut away from, and the preview set iterates in an
		// unpredictable order — without sorting, crossings would flip their
		// winner from tick to tick and flicker
		pieces.sort(Comparator.comparing(Piece::key));
		drawWithoutOverlap(pieces);
	}

	/**
	 * Collects the floating beams of one connection of the previewed path. The
	 * connection object is looked up on the target panel (Create stores links
	 * in the target's {@code targetedBy}); its path is walked from the
	 * target's slot anchor exactly like the hit tester does.
	 */
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

		// Zero-length runs (a step that does not move in path space, e.g. the
		// panel-facing entry step) carry no geometry but still split the
		// polyline; dropping them up front keeps their neighbours adjacent
		List<Run> drawn = new ArrayList<>();
		for (Run run : runs) {
			if (run.start().distanceToSqr(run.end()) >= 1.0E-6)
				drawn.add(run);
		}

		for (int i = 0; i < drawn.size(); i++) {
			Run run = drawn.get(i);
			Vec3 dir = run.end().subtract(run.start()).normalize();
			// Runs butt end to end instead of overlapping: at a right-angle
			// joint the outgoing run is pulled back by half a beam width and
			// the corner piece below covers the quadrant the two beams would
			// leave open. Overlapping them (the earlier approach) made their
			// shared top faces double-blend into a visibly darker square on
			// every corner. Only the two gauge ends reach further, into their
			// panel slot.
			double reachStart = i == 0 ? -GAUGE_REACH : HALF_BEAM;
			double reachEnd = i == drawn.size() - 1 ? GAUGE_REACH : 0;
			Vec3 lo = run.start().add(dir.scale(reachStart));
			Vec3 hi = run.end().add(dir.scale(reachEnd));
			if (hi.subtract(lo).dot(dir) > 1.0E-6)
				pieces.add(new Piece(keyPrefix + "_" + i,
					crossExpandedBox(lo, hi, worldAxisOf(dir), HALF_BEAM), dir));
			// Corner fill: the outer quadrant of the joint, exactly abutting
			// both beams (never overlapping them)
			if (i > 0) {
				Run previous = drawn.get(i - 1);
				Vec3 incoming = previous.end().subtract(previous.start()).normalize();
				pieces.add(new Piece(keyPrefix + "_c" + i,
					cornerBox(run.start(), incoming, dir), null));
			}
		}
	}

	/** One cuboid of the preview: its outline key, its box, and its run direction. */
	private record Piece(String key, AABB box, Vec3 axis) {}

	/**
	 * Draws the preview's cuboids so that no two of them occupy the same space.
	 * Beams that overlap double-blend and z-fight against each other — where
	 * two runs of the chain crossed, that showed up as a darker, stepped patch
	 * at the crossing. Each piece is therefore cut back to whatever the
	 * previously drawn pieces have not already covered; what is cut away is
	 * exactly the volume the neighbour paints anyway, in the same colour, so
	 * the seam disappears instead of moving.
	 */
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

	/**
	 * The parts of {@code piece} that the already placed cuboids do not cover.
	 * Only stretches where a neighbour swallows the piece's whole cross-section
	 * are cut: that is the case for beams meeting head-on (a crossing, or two
	 * runs sharing a route), and there the cut is exact. Partial lateral
	 * overlaps, where cutting would leave a notch instead of removing a
	 * duplicate, are left alone.
	 */
	private static List<AABB> uncovered(Piece piece, List<Piece> placed) {
		AABB box = piece.box();
		// Corner fills are blocks rather than beams — they are never split, and
		// later beams cut themselves away from them instead
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

	/** Whether {@code other} spans {@code box}'s whole cross-section, so cutting it out leaves no notch. */
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

	/** {@code box} with its range along {@code axis} replaced by [from, to]. */
	private static AABB slice(AABB box, Axis axis, double from, double to) {
		if (axis == Axis.X)
			return new AABB(from, box.minY, box.minZ, to, box.maxY, box.maxZ);
		if (axis == Axis.Y)
			return new AABB(box.minX, from, box.minZ, box.maxX, to, box.maxZ);
		return new AABB(box.minX, box.minY, from, box.maxX, box.maxY, to);
	}

	/** One straight piece of the previewed polyline, in world coordinates. */
	private record Run(Vec3 start, Vec3 end) {}

	/**
	 * The piece that closes a right-angle joint: it runs from the joint vertex
	 * half a beam width on along the incoming direction, and spans the outgoing
	 * run's full beam width across it — exactly the quadrant the two butted
	 * beams leave open. It touches both of them instead of overlapping, so
	 * nothing here double-blends.
	 */
	private static AABB cornerBox(Vec3 vertex, Vec3 incoming, Vec3 outgoing) {
		// the incoming axis supplies one side (the vertex is its lower end),
		// the outgoing axis is spanned symmetrically around the vertex
		Vec3 lo = vertex.add(outgoing.scale(-HALF_BEAM));
		Vec3 hi = vertex.add(incoming.scale(HALF_BEAM)).add(outgoing.scale(HALF_BEAM));
		// the joint sits in the panel's plane; both beam directions are in it,
		// so their cross product names the axis the fill has to be thick along
		Axis normal = worldAxisOf(incoming.cross(outgoing));
		double ex = normal == Axis.X ? HALF_BEAM : 0;
		double ey = normal == Axis.Y ? HALF_BEAM : 0;
		double ez = normal == Axis.Z ? HALF_BEAM : 0;
		return new AABB(Math.min(lo.x, hi.x) - ex, Math.min(lo.y, hi.y) - ey, Math.min(lo.z, hi.z) - ez,
			Math.max(lo.x, hi.x) + ex, Math.max(lo.y, hi.y) + ey, Math.max(lo.z, hi.z) + ez);
	}

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
		return toWorld(origin, xRotDeg, yRotDeg, pathX, FLOAT_HEIGHT, pathZ);
	}

	/** Path space → world with an explicit height, for geometry on the panel plane itself. */
	private static Vec3 toWorld(BlockPos origin, float xRotDeg, float yRotDeg, double pathX, double pathY,
		double pathZ) {
		Vec3 v = new Vec3(pathX, pathY, pathZ);
		v = VecHelper.rotateCentered(v, 180, Axis.Y);
		v = VecHelper.rotateCentered(v, xRotDeg + 90, Axis.X);
		v = VecHelper.rotateCentered(v, yRotDeg, Axis.Y);
		return v.add(Vec3.atLowerCornerOf(origin));
	}

	/**
	 * The world box a gauge panel occupies, used by {@link PathDyeOverlay} to
	 * box the gauge that a right-click would confirm. Mirrors Create's own
	 * highlight for its connection flow: the slot's centre, thickened by 3/16
	 * along the two axes of the panel's plane, so the box hugs the wall instead
	 * of bulging out of it.
	 */
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
		// the panel normal is the path-space "up" mapped into the world; the
		// box is flat against the plane, so only the other two axes thicken
		Vec3 normal = toWorld(BlockPos.ZERO, xRotDeg, yRotDeg, 0, 1, 0)
			.subtract(toWorld(BlockPos.ZERO, xRotDeg, yRotDeg, 0, 0, 0));
		double nx = Math.abs(normal.x);
		double ny = Math.abs(normal.y);
		double nz = Math.abs(normal.z);
		return new AABB(center, center).inflate(nx < 0.5 ? 3 / 16.0 : 0, ny < 0.5 ? 3 / 16.0 : 0,
			nz < 0.5 ? 3 / 16.0 : 0);
	}
}
