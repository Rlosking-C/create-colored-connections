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

public final class DyeEffects {

	private DyeEffects() {}

	public static void play(ServerLevel level, FactoryPanelPosition from, FactoryPanelPosition to, DyeColor dye) {
		if (!CreateCCConfig.DYE_EFFECTS.get())
			return;
		level.playSound(null, to.pos(), SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 0.5f, 1.0f);
		trail(level, from, to, dustOptions(dye));
	}

	public static void play(ServerLevel level, List<ConnectionKey> path, DyeColor dye) {
		if (!CreateCCConfig.DYE_EFFECTS.get() || path.isEmpty())
			return;
		FactoryPanelPosition end = path.get(path.size() - 1).to();
		level.playSound(null, end.pos(), SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 0.5f, 1.0f);
		DustParticleOptions options = dustOptions(dye);
		for (ConnectionKey key : path)
			trail(level, key.from(), key.to(), options);
	}

	private static DustParticleOptions dustOptions(DyeColor dye) {
		int rgb = dye == null ? 0x8A8A8A : dye.getTextureDiffuseColor();
		Vector3f color = new Vector3f(
			((rgb >> 16) & 0xFF) / 255f,
			((rgb >> 8) & 0xFF) / 255f,
			(rgb & 0xFF) / 255f);
		return new DustParticleOptions(color, 1.0f);
	}

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

	private static Vec3 pathToWorld(double x, double z, float xRotDeg, float yRotDeg) {
		Vec3 v = new Vec3(x, 0, z);
		v = v.subtract(0.5, 0.5, 0.5);
		v = VecHelper.rotate(v, 180, Axis.Y);
		v = VecHelper.rotate(v, xRotDeg + 90, Axis.X);
		v = VecHelper.rotate(v, yRotDeg, Axis.Y);
		return v.add(0.5, 0.5, 0.5);
	}

	private static void puff(ServerLevel level, FactoryPanelPosition pos, DustParticleOptions options) {
		Vec3 center = Vec3.atCenterOf(pos.pos());
		level.sendParticles(options, center.x, center.y, center.z, 10, 0.35, 0.35, 0.35, 0.01);
	}
}
