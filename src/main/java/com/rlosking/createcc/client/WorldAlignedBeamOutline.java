package com.rlosking.createcc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.outliner.Outline;
import net.createmod.catnip.render.PonderRenderTypes;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class WorldAlignedBeamOutline extends Outline {

	private final AABB box;
	private final float fullWidth;

	public WorldAlignedBeamOutline(AABB box, float fullWidth) {
		this.box = box;
		this.fullWidth = fullWidth;
	}

	@Override
	public void render(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, float pt) {
		VertexConsumer consumer = buffer.getBuffer(PonderRenderTypes.outlineSolid());
		params.loadColor(colorTemp);
		Vector4f color = colorTemp;
		AABB shrunk = shrinkTowardRunAxis(box, params.getLineWidth() / fullWidth);
		ms.pushPose();
		ms.translate(-camera.x, -camera.y, -camera.z);
		bufferCuboid(ms.last(), consumer,
			new Vector3f((float) shrunk.minX, (float) shrunk.minY, (float) shrunk.minZ),
			new Vector3f((float) shrunk.maxX, (float) shrunk.maxY, (float) shrunk.maxZ),
			color, LightTexture.FULL_BRIGHT, true);
		ms.popPose();
	}

	private static AABB shrinkTowardRunAxis(AABB box, float factor) {
		if (factor >= 1.0F)
			return box;
		double sx = box.getXsize();
		double sy = box.getYsize();
		double sz = box.getZsize();
		if (sx >= sy && sx >= sz)
			return resizeCross(box, factor, true, false, false);
		if (sy >= sz)
			return resizeCross(box, factor, false, true, false);
		return resizeCross(box, factor, false, false, true);
	}

	private static AABB resizeCross(AABB box, float factor, boolean keepX, boolean keepY, boolean keepZ) {
		double cx = (box.minX + box.maxX) / 2;
		double cy = (box.minY + box.maxY) / 2;
		double cz = (box.minZ + box.maxZ) / 2;
		double hx = box.getXsize() / 2 * (keepX ? 1 : factor);
		double hy = box.getYsize() / 2 * (keepY ? 1 : factor);
		double hz = box.getZsize() / 2 * (keepZ ? 1 : factor);
		return new AABB(cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz);
	}
}
