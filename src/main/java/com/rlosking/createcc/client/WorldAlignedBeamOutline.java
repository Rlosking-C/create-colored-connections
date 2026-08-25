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

/**
 * One preview beam: an axis-aligned cuboid submitted directly in world
 * coordinates, drawn through Catnip's outline pipeline (the same
 * entity-solid layer {@code Outliner.showLine} uses).
 *
 * <p>Why not {@code Outliner.showLine}? Catnip's {@code LineOutline} rotates
 * the vertex pose so the cuboid's local +Z follows the line direction and,
 * with {@code disableLineNormals}, labels every face with the LOCAL up
 * vector. For a horizontal beam that local up survives as world up, so the
 * entity-solid shader's directional lights give it full brightness — but for
 * a VERTICAL beam the pose's 90-degree pitch rotates that normal sideways,
 * and the same lights cut its brightness to roughly three quarters. That is
 * exactly the "vertical beams look darker" artifact. Rendering the cuboid
 * through an UNROTATED pose (only translated by -camera) keeps every face's
 * normal pointing at world up, so horizontal and vertical runs of the
 * preview glow with identical intensity.</p>
 *
 * <p>Because all faces share one normal, one color and one full-bright
 * lightmap, the deliberate overlaps between neighbouring beams (the corner
 * fills where two runs meet) shade to identical pixels, so no seams or
 * z-fighting become visible where the cuboids intersect.</p>
 */
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

	/**
	 * While the outline fades out, Catnip shrinks {@code getLineWidth()}
	 * (it tracks the fading alpha). Mirror that behaviour by collapsing the
	 * cuboid's cross-section toward its own long axis — the same thinning
	 * {@code LineOutline} applies to its beams — instead of lingering at
	 * full thickness and popping out.
	 */
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

	/** Shrinks the two cross-section axes by {@code factor}, keeps the run axis. */
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
