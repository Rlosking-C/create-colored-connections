# Plan — Create: Colored Connections 市场调研

- Language / audience: 中文；听众为模组作者（单人开发），需要可直接执行的版本规划
- Decision the report supports: 0.5.x 及之后三个版本的功能与分发优先级
- Evidence: Modrinth / GitHub / 官方 Maven 接口直连查询（已复核）、Create 本体与相邻模组的 Modrinth 计数、作者提供的 Create 版本页截图（与接口交叉一致）、项目自带源码与 README/CHANGELOG/截图（本地可核实）；CurseForge 数据仅单次页面快照，标注为未复核
- 核查原则：官方接口或本地文件可复核的标「已复核」；只有单次抓取且复核受阻的标「未复核」；取不到原始数据的第三方统计（加载器占比）已剔除
- 优先级来源：P0 两项为兼容基线确认（已完成切换与 runClient 验证）与平台描述同步；P1 四项由作者确认（Configured 配置兼容、批量染色预览渲染改善、染色复用原版使用动作、多语言）。经作者说明后已移除两项建议：1.20.1 移植（作者澄清无此承诺）、GitHub 反馈入口（作者确认反馈以评论区为主）

Color preset: Business Blue — cue: 市场与竞品分析、面向开发决策的正式报告类型
Intro mode: contained — cue: 正式技术与市场研究，首屏无合适视觉承载层，产品截图下移为正文证据图

## Sections

1. 结论速览（指标卡 4 项，全部为可复核数据 + 5 条核心判断）
2. 产品与代码现状（功能矩阵表、实现要点、缺口清单、证据图 ×2）
3. 市场表现（双平台数据表含核查标注、版本下载量图、渠道与曝光分析）
4. 竞争格局（竞品对比表、相邻赛道规模图、生态位判断）
5. 生态与版本环境（Create 版本线、Create 1.21.1 版本集中度图、Fabric 判断、1.20.1 说明）
6. 需求信号与反馈闭环（信号清单表、玩家原声引用含核查与澄清）
7. 后续开发优先级建议（P0 ×2 / P1 ×6 作者确认 / P2 ×6 可选，编号 1–14 连续）
8. 风险与应对（风险矩阵表）
9. 数据核查说明（关键数据的核查方式与状态）
10. 结论
11. 资料来源（18 条，含核查状态标注）

## Visuals

- Chart 1（§3）：各版本下载量，Modrinth × CurseForge 双系列分组柱状（CurseForge 系列标注未复核）
- Chart 2（§4）：相邻赛道规模对比，对数刻度单系列柱状
- Chart 3（§5）：Create 1.21.1 线各版本下载量分布（接口直查，全部已复核）
- Figures（§2）：原版 / 着色对比图、16 色效果图（项目自带截图）

Intro media: FALLBACK — 游戏截图构图不适合作首屏文本承载层，改为正文证据图（保留来源标注）

## Companion deliverable

- `mod-description`（中英双语发布描述）：`d:\GAMES\MCMod\colored-connections\DOC\模组描述-0.4.1-中英双语.md`，供 Modrinth / CurseForge / MC 百科直接粘贴
