package com.rlosking.createcc;

import java.util.List;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Success feedback for dyeing actions: one sound cue plus dye-colored dust
 * particles trailing along the dyed link itself (or the whole dyed path).
 *
 * <p>The sound is the exact cue Create itself plays when two gauges are
 * successfully linked — vanilla {@code AMETHYST_BLOCK_PLACE} on the BLOCKS
 * source at half volume — so dyeing feedback lives in the same sound family
 * as the player's other gauge interactions instead of inventing a new one.</p>
 *
 * <p>The particles follow the actual polyline of the link, not just its two
 * ends: the trail reuses the path-space coordinate frame of
 * {@link ConnectionHitTester} (same rotation chain the renderer builds),
 * walks the connection's segment list, and transforms each segment midpoint
 * back into world coordinates. A link that bends around a corner gets its
 * particles on the bend, where the line actually is.</p>
 */
public final class DyeEffects {

	private DyeEffects() {}

	/**
	 * Plays the feedback for one dyeing action.
	 *
	 * @param level the server level the action happened in
	 * @param from  one end of the action (single link source / path start)
	 * @param to    the other end (link target / path end)
	 * @param dye   the applied dye; null when the color was cleared
	 *              (black dye) — neutral gray dust is used then
	 */
	public static void play(ServerLevel level, FactoryPanelPosition from, FactoryPanelPosition to, DyeColor dye) {
		if (!CreateCCConfig.DYE_EFFECTS.get())
			return;
		level.playSound(null, to.pos(), SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 0.5f, 1.0f);
		trail(level, from, to, dustOptions(dye));
	}

	/**
	 * Plays the feedback for a path-dyeing action: the sound at the path's
	 * end, and the dust trail along every link of the path.
	 *
	 * @param path the dyed connection keys, in start-to-end order
	 */
	public static void play(ServerLevel level, List<ConnectionKey> path, DyeColor dye) {
		if (!CreateCCConfig.DYE_EFFECTS.get() || path.isEmpty())
			return;
		FactoryPanelPosition end = path.get(path.size() - 1).to();
		level.playSound(null, end.pos(), SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 0.5f, 1.0f);
		DustParticleOptions options = dustOptions(dye);
		for (ConnectionKey key : path)
			trail(level, key.from(), key.to(), options);
	}

	/** Dust in the applied dye's own color (the wool/textured palette the renderer also uses). */
	private static DustParticleOptions dustOptions(DyeColor dye) {
		int rgb = dye == null ? 0x8A8A8A : dye.getTextureDiffuseColor();
		Vector3f color = new Vector3f(
			((rgb >> 16) & 0xFF) / 255f,
			((rgb >> 8) & 0xFF) / 255f,
			(rgb & 0xFF) / 255f);
		return new DustParticleOptions(color, 1.0f);
	}

	/**
	 * Dust trail along one link's polyline. The connection object lives on
	 * the target panel's {@code targetedBy} map (source → this panel); when
	 * it cannot be resolved (moved/removed in the same tick), the two ends
	 * get a fallback puff so the action still has feedback.
	 */
	private static void trail(ServerLevel level, FactoryPanelPosition from, FactoryPanelPosition to,
			DustParticleOptions options) {
		FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(level, to);
		FactoryPanelConnection connection = behaviour == null ? null : behaviour.targetedBy.get(from);
		BlockState state = behaviour == null ? null : behaviour.blockEntity.getBlockState();
		List<Direction> path = connection == null ? null : connection.getPath(level, state, to);
		if (path == null || path.isEmpty()) {
			puff(level, from, options);
			puff(level, to, options);
			return;
		}

		// Path-space anchor of the target panel's slot (same formula the hit
		// tester uses), then one particle pair per segment midpoint
		float xRotDeg = Mth.RAD_TO_DEG * FactoryPanelBlock.getXRot(state);
		float yRotDeg = Mth.RAD_TO_DEG * FactoryPanelBlock.getYRot(state);
		Vec3 origin = Vec3.atLowerCornerOf(behaviour.getPos());
		double prevX = behaviour.slot.xOffset * 0.5 + 0.25;
		double prevZ = behaviour.slot.yOffset * 0.5 + 0.25;
		for (Direction d : path) {
			double nextX = prevX + d.getStepX() * 0.5;
			double nextZ = prevZ + d.getStepZ() * 0.5;
			Vec3 mid = pathToWorld((prevX + nextX) / 2, (prevZ + nextZ) / 2, xRotDeg, yRotDeg).add(origin);
			level.sendParticles(options, mid.x, mid.y, mid.z, 2, 0.08, 0.08, 0.08, 0.0);
			prevX = nextX;
			prevZ = nextZ;
		}
	}

	/**
	 * Path space → world (block-local) coordinates: the exact inverse of the
	 * transform {@link ConnectionHitTester} applies when picking, so the
	 * trail lands on the polyline the renderer draws. Path space's y≈0 is
	 * the plane the lines live on.
	 */
	private static Vec3 pathToWorld(double x, double z, float xRotDeg, float yRotDeg) {
		Vec3 v = new Vec3(x, 0, z);
		v = v.subtract(0.5, 0.5, 0.5);
		v = VecHelper.rotate(v, 180, Axis.Y);
		v = VecHelper.rotate(v, xRotDeg + 90, Axis.X);
		v = VecHelper.rotate(v, yRotDeg, Axis.Y);
		return v.add(0.5, 0.5, 0.5);
	}

	/** Spawns a small cloud of dust particles around one panel position. */
	private static void puff(ServerLevel level, FactoryPanelPosition pos, DustParticleOptions options) {
		Vec3 center = Vec3.atCenterOf(pos.pos());
		level.sendParticles(options, center.x, center.y, center.z, 10, 0.35, 0.35, 0.35, 0.01);
	}
}
