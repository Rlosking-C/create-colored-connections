package com.colconn.createcc;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * 机械动力：彩色连接线（Create: Colored Connections）
 *
 * <p>为 Create 6.0 工厂仪表（Factory Gauge）配方模式下的连接线提供 16 色染色功能，
 * 解决大量连接线交叉重叠时难以分辨的问题。</p>
 *
 * <p>核心设计：
 * <ul>
 *   <li>染料右键染色，黑色染料 = 恢复原版状态色</li>
 *   <li>新建连接时，若来源仪表的入线颜色唯一则继承该颜色（仅创建时继承，不动态级联）</li>
 *   <li>多色同格共显：2 色并排双条 / 3-4 色车道均分 / 5+ 色轮播 + 悬停置顶</li>
 *   <li>染色以纹理 tint 实现（保留连接线搓衣板斜纹），与 Create 视觉风格一致</li>
 * </ul></p>
 */
@Mod(ColoredConnections.MODID)
public class ColoredConnections {

    /** 模组 ID，与 neoforge.mods.toml 中保持一致 */
    public static final String MODID = "create_colored_connections";

    /**
     * 模组入口（NeoForge 1.21.1 通过构造器注入事件总线）
     */
    public ColoredConnections(IEventBus modBus, ModContainer container) {
        // 骨架阶段：仅验证依赖解析与 Mixin 编译，无注册内容
    }
}
