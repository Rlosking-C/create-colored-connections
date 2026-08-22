package com.colconn.createcc.mixin.client;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 工厂仪表连接线渲染器注入点。
 *
 * <p>Create 的 {@code FactoryPanelRenderer.renderPath()} 负责绘制配方模式下的
 * 连接线，所有颜色逻辑（灰色 / 红色状态色）最终汇聚于
 * {@code connectionSprite.color(color)} 单次调用。</p>
 *
 * <p>后续阶段将在此注入染料着色逻辑：
 * <ul>
 *   <li>单色 tint：染料色乘算纹理斜纹</li>
 *   <li>多色共显：并排双条 / 车道 / 轮播</li>
 *   <li>悬停置顶：十字光标指向的连接线临时提升渲染层级</li>
 * </ul></p>
 */
@Mixin(FactoryPanelRenderer.class)
public abstract class FactoryPanelRendererMixin {
    // 骨架阶段：验证 Create 依赖与 Mixin 编译，注入点待精读 renderPath 后实现
}
