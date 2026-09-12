package com.rlosking.createcc.mixin.factorycontroller.client;

import io.github.nbcss.createfactorycontroller.content.render.TiledSpriteRenderer;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes Factory Controller's per-atlas GUI sprite {@link RenderType}.
 *
 * <p>The dye underlay needs to push quads with half-pixel coordinates into
 * the same render batch as the wires, but TiledSpriteRenderer's public API
 * only accepts integer quad coordinates. This accessor hands over the
 * renderer's cached render type so the underlay can acquire a
 * {@link com.mojang.blaze3d.vertex.VertexConsumer} itself.</p>
 */
@Mixin(TiledSpriteRenderer.class)
public interface TiledSpriteRendererAccessor {

	@Accessor("renderType")
	RenderType createcc$getRenderType();
}
