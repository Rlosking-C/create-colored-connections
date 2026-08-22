# Create: Colored Connections

**机械动力：彩色连接线**

Colorize the recipe-mode connection lines of Create's Factory Gauges with the 16 vanilla dyes, so that heavily crossed networks stay readable at a glance.

为机械动力工厂仪表的配方模式连接线提供 16 色染色，让大量交叉的网络一眼可辨。

---

## Features / 功能

- **Right-click a link with any dye** to color it. Lines are drawn on the wall between gauges — point at the line itself, not the gauge panel.
- **Black dye = reset.** Restores the vanilla status color.
- **Smart inheritance.** A newly created link inherits the source gauge's incoming color — but only when all incoming links share exactly one color. Mixed or uncolored inputs stay vanilla.
- **Status colors stay intact.** Idle lines are fully dyed; active lines (in progress / satisfied / failed / flashing) keep their vanilla status-colored core and gain a thin 1px dye border on each side. Animations like the scrolling texture and restock flashing are untouched.
- **Hover lift.** Looking at any link smoothly lifts the whole line above its neighbors (a few microns — only the occlusion order changes), so you can always tell which line is which where links cross or overlap.
- **Link lines are never dyed.** Redstone and display link lines carry status semantics in their color and are left fully vanilla.

- **手持任意染料右键连接线**即可染色。线画在仪表之间的墙面上——请直接指向线本身，而非仪表面板。
- **黑色染料 = 还原。** 恢复原版状态色。
- **智能继承。** 新建的连接会继承来源仪表的入线颜色——仅当全部入线颜色唯一时；混杂或未染色的入线保持原版表现。
- **状态色完整保留。** 待机线整线染色；状态线（进行中/已完成/中止/闪烁）保留原版状态色主体，两侧各获得 1px 染料边框。搓衣板滚动纹理与补货闪烁动画不受影响。
- **注视浮上。** 准星注视任意连接线时，整条线平滑浮至其他线之上（仅几微米——只改变遮挡顺序），交叉或重叠处也能分清每条线的归属。
- **链接线永不染色。** 红石链接线与显示器链接线的颜色承载状态语义，完全交给原版。

## Requirements / 前置

| | |
|---|---|
| Minecraft | 1.21.1 |
| Loader | NeoForge 21.1.x |
| Create | 6.0.0 or higher / 或更高版本 |

Client **and** server for multiplayer. / 多人游戏需客户端与服务端同时安装。

## Data & Compatibility / 数据与兼容

- Colors are stored per dimension in the world save and survive panel relocation and world reload.
- Colors sync to players on login, dimension change, and chunk load — nothing to configure.
- No config file, no commands; vanilla dye mechanics only.

- 染色数据按维度保存在存档中，仪表迁移与存档重载后不丢失。
- 登录、切换维度、区块加载时自动同步——无需任何配置。
- 无配置文件、无命令，仅使用原版染料机制。

## Building / 构建

```powershell
./gradlew build
```

The jar lands in `build/libs`. / 构建产物位于 `build/libs`。

## License / 许可证

[MIT](LICENSE)
