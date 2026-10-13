package com.rlosking.createcc.mixin.factorycontroller.client;

import io.github.nbcss.createfactorycontroller.content.render.TiledSpriteRenderer;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TiledSpriteRenderer.class)
public interface TiledSpriteRendererAccessor {

	@Accessor("renderType")
	RenderType createcc$getRenderType();
}
