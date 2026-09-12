package com.rlosking.createcc.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.rlosking.createcc.mixin.factorycontroller.client.TiledSpriteRendererAccessor;

import io.github.nbcss.createfactorycontroller.content.render.TiledSpriteRenderer;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector2i;

import java.util.List;
import java.util.Map;

/**
 * Draws the dye underlay for an active GUI wire: Factory Controller's own
 * connection texture tinted with the dye color, covering the wire's path
 * expanded two pixels perpendicular on each side (50% of the 4px core, the
 * same proportion as the world renderer's widened underlay), so the
 * status-colored core rendered afterwards leaves a <em>textured</em> dye
 * border — the GUI counterpart of the world look, which also shows the
 * washboard texture (not a flat color) at the dye edge.
 *
 * <p>The path walk, atlas rects and frame timing replicate FC 1.2.0's
 * {@code VirtualConnectionRenderer.drawPath}. They are copied here because
 * that class keeps its subtexture tables private, and its public sprite API
 * only accepts integer quad coordinates — a sub-pixel-precise border needs
 * float vertices emitted directly, mirroring
 * {@code TiledSpriteRenderer.buildQuad}
 * (quad-local pixel UV, atlas sub-rect on the UV1/UV2 attributes consumed by
 * FC's tiled-sprite shader).</p>
 *
 * <p>Although this is a plain helper (not a mixin), it must stay out of the
 * mixin package declared by {@code createcc-fc.mixins.json}: the mixin
 * framework only allows real mixin classes there. It is referenced only from
 * {@code ConnectionWidgetMixin}, which never loads when Factory Controller
 * is absent.</p>
 */
public final class DyeUnderlay {

	private DyeUnderlay() {}

	// --- Copied from FC 1.2.0 VirtualConnectionRenderer (pinned dependency) ---
	private static final int CELL = 16;
	private static final int FRAME_SIZE = 16;
	private static final float FRAME_TIME = 2f;
	private static final int N_FRAMES = 8;
	private static final ResourceLocation TEX_STATIC =
			ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "textures/gui/connection/static.png");
	private static final ResourceLocation TEX_ANIMATED =
			ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "textures/gui/connection/animated.png");

	/**
	 * How far the underlay peeks past the 4px core on each side, in canvas px.
	 * Scaled from the world renderer's look (2px core widened 2×, 1px peeking
	 * per side → border = 50% of core): the GUI core is 4px, so the border is
	 * 2px per side — the same border-to-core proportion as a dyed wall link.
	 */
	private static final float BORDER = 2f;

	private enum Dir {
		UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

		final int x, y;

		Dir(int x, int y) {
			this.x = x;
			this.y = y;
		}
	}

	/** Subtexture rect in the connection atlas: u, v, w, h, anchor ox, anchor oy. */
	private record Sub(int u, int v, int w, int h, int ox, int oy) {}

	private static final Map<Dir, Sub> LINES = Map.of(
			Dir.UP, new Sub(0, 0, 4, 8, 0, 0),
			Dir.DOWN, new Sub(4, 0, 4, 8, 0, 0),
			Dir.LEFT, new Sub(0, 8, 8, 4, 0, 0),
			Dir.RIGHT, new Sub(0, 12, 8, 4, 0, 0));
	private static final Map<Dir, Sub> HEADS = Map.of(
			Dir.UP, new Sub(8, 0, 4, 4, 2, -6),
			Dir.DOWN, new Sub(12, 0, 4, 4, 2, 10),
			Dir.LEFT, new Sub(8, 4, 4, 4, -6, 2),
			Dir.RIGHT, new Sub(12, 4, 4, 4, 10, 2));
	private static final Map<Dir, Sub> TAILS = Map.of(
			Dir.UP, new Sub(0, 0, 4, 2, 2, 8),
			Dir.DOWN, new Sub(4, 6, 4, 2, 2, -6),
			Dir.LEFT, new Sub(0, 8, 2, 4, 8, 2),
			Dir.RIGHT, new Sub(6, 12, 2, 4, -6, 2));

	/** Render types are cached per texture inside FC's renderer; fetch once per atlas. */
	private static RenderType staticType;
	private static RenderType animatedType;

	/**
	 * Grabs the render type of the connection-texture batch. The four-argument
	 * factory is the same one FC's own sprite lookup uses — the one-argument
	 * overload resolves through the stitched GUI sprite atlas instead, which
	 * would yield a different (wrong) texture binding. The u/v offsets and the
	 * dummy tile scaling are never read: only the per-atlas render type is
	 * borrowed, the quads are emitted by {@link #quad}.
	 */
	private static RenderType renderType(boolean animated) {
		if (animated) {
			if (animatedType == null)
				animatedType = ((TiledSpriteRendererAccessor) (Object) TiledSpriteRenderer.create(
						TEX_ANIMATED, 0, 0, new GuiSpriteScaling.Tile(1, 1))).createcc$getRenderType();
			return animatedType;
		}
		if (staticType == null)
			staticType = ((TiledSpriteRendererAccessor) (Object) TiledSpriteRenderer.create(
					TEX_STATIC, 0, 0, new GuiSpriteScaling.Tile(1, 1))).createcc$getRenderType();
		return staticType;
	}

	/**
	 * Submits the whole wire path in dye tint, two pixels wider than the core
	 * on each side, into the same batch the wires render in — anything drawn
	 * afterwards (the vanilla status core) stacks on top.
	 */
	public static void draw(GuiGraphics gfx, List<Vector2i> path, int argb, boolean animated) {
		if (path == null || path.size() < 2)
			return;
		VertexConsumer buffer = gfx.bufferSource().getBuffer(renderType(animated));
		int frame = animated ? (int) (AnimationTickHolder.getRenderTime() / FRAME_TIME) % N_FRAMES : 0;

		for (int i = 0; i < path.size() - 1; i++) {
			Vector2i a = path.get(i), b = path.get(i + 1);

			Dir dir;
			if (a.y > b.y)
				dir = Dir.UP;
			else if (a.y < b.y)
				dir = Dir.DOWN;
			else if (a.x > b.x)
				dir = Dir.LEFT;
			else if (a.x < b.x)
				dir = Dir.RIGHT;
			else
				continue;

			boolean tail = i == 0;
			boolean head = i == path.size() - 2;

			Vector2i pa = new Vector2i(a).mul(CELL).add(CELL / 2, CELL / 2);
			Vector2i pb = new Vector2i(b).mul(CELL).add(CELL / 2, CELL / 2);

			Vector2i lineStart = new Vector2i(pa).add(dir.x * CELL / 4, dir.y * CELL / 4);
			Vector2i lineEnd = new Vector2i(pb).sub(dir.x * CELL / 4, dir.y * CELL / 4);
			if (tail)
				lineStart.add(dir.x * CELL / 2, dir.y * CELL / 2);
			if (head)
				lineEnd.sub(dir.x * CELL / 2, dir.y * CELL / 2);

			int lineLength = (lineEnd.x - lineStart.x) * dir.x + (lineEnd.y - lineStart.y) * dir.y;
			if (lineLength >= 0) {
				Sub st = LINES.get(dir);
				int minX = Math.min(lineStart.x, lineEnd.x);
				int minY = Math.min(lineStart.y, lineEnd.y);
				int sizeX = Math.abs(lineStart.x - lineEnd.x);
				int sizeY = Math.abs(lineStart.y - lineEnd.y);
				quad(buffer, gfx.pose(), dir,
						minX - st.w / 2f, minY - st.h / 2f,
						minX - st.w / 2f + sizeX + st.w, minY - st.h / 2f + sizeY + st.h,
						st, frame, argb);
			}

			if (tail) {
				Sub st = TAILS.get(dir);
				quad(buffer, gfx.pose(), dir,
						pa.x - st.ox, pa.y - st.oy, pa.x - st.ox + st.w, pa.y - st.oy + st.h,
						st, frame, argb);
			}
			if (head) {
				Sub st = HEADS.get(dir);
				quad(buffer, gfx.pose(), dir,
						pb.x - st.ox, pb.y - st.oy, pb.x - st.ox + st.w, pb.y - st.oy + st.h,
						st, frame, argb);
			}
		}
	}

	/**
	 * One sprite quad, expanded perpendicular to the wire direction. The UV
	 * pattern mirrors TiledSpriteRenderer.buildQuad: UV0 spans the tile size
	 * in quad-local pixels (so the tile stretches over the expanded quad, its
	 * outer pixels forming the visible border), while UV1/UV2 carry the atlas
	 * sub-rect and frame offset for FC's tiled-sprite shader.
	 */
	private static void quad(VertexConsumer buffer, PoseStack pose, Dir dir,
			float x1, float y1, float x2, float y2, Sub st, int frame, int argb) {
		if (dir == Dir.UP || dir == Dir.DOWN) {
			x1 -= BORDER;
			x2 += BORDER;
		} else {
			y1 -= BORDER;
			y2 += BORDER;
		}
		int u1 = st.u;
		int v1 = st.v + frame * FRAME_SIZE;
		int u2 = st.u + st.w;
		int v2 = v1 + st.h;
		PoseStack.Pose p = pose.last();
		buffer.addVertex(p, x1, y1, 0).setColor(argb).setUv(0, 0).setUv1(u1, v1).setUv2(u2, v2);
		buffer.addVertex(p, x1, y2, 0).setColor(argb).setUv(0, v2 - v1).setUv1(u1, v1).setUv2(u2, v2);
		buffer.addVertex(p, x2, y2, 0).setColor(argb).setUv(u2 - u1, v2 - v1).setUv1(u1, v1).setUv2(u2, v2);
		buffer.addVertex(p, x2, y1, 0).setColor(argb).setUv(u2 - u1, 0).setUv1(u1, v1).setUv2(u2, v2);
	}
}
