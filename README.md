# Create: Colored Connections

Colorize the recipe-mode connection lines of Create's Factory Gauges with the 16 vanilla dyes, so that heavily crossed networks stay readable at a glance.

## Before / After

The same network, once vanilla and once color-coded:

**Vanilla** — link colors only reflect recipe status, so you can't tell which line belongs to which recipe:

![The same gauge network in vanilla, uncolored](images/vanilla-network.png)

**With Colored Connections** — each recipe keeps its own dye color, and every link stays traceable at a glance:

![The same gauge network, color-coded with dyes](images/dense-network.png)

## Features

- **Right-click a link with any dye** to color it. Lines are drawn on the wall between gauges — point at the line itself, not the gauge panel. By default the dye is never consumed — not even in survival mode — so recolor as often as you like (a config option can make dyeing cost one dye per action).
- **Black dye = reset.** Restores the vanilla status color.
- **Path dyeing.** Shift+right-click a gauge to start a path, then simply sweep your crosshair over further gauges: each one joins the chain, connected to the previous gauge along its own shortest route — the sweep decides the branch, so the dyed path is exactly the route you traced. While building, every pending link carries a floating green beam. Hover the last gauge again to undo it, right-click the last gauge to dye the whole chain in one action, right-click anywhere else to cancel.
- **Tactile feedback.** A successful dyeing plays the same sound Create uses when two gauges link, plus a small puff of dust in the applied dye's color.
- **Smart inheritance.** A newly created link inherits the source gauge's incoming color — but only when all incoming links share exactly one color. Mixed or uncolored inputs stay vanilla.
- **Status colors stay intact.** Idle lines are fully dyed; active lines (in progress / satisfied / failed / flashing) keep their vanilla status-colored core and gain a thin 1px dye border on each side. Animations like the scrolling texture and restock flashing are untouched.
- **Hover lift.** Looking at any link smoothly lifts the whole line above its neighbors (a few microns — only the occlusion order changes), so you can always tell which line is which where links cross or overlap.
- **Sticky hover.** Once a line is hovered it stays picked while the crosshair is on it; small mouse movements over crossing or overlapping lines no longer make the highlight jump between them. The dye click targets exactly the line that is lifted on screen.
- **Link lines are never dyed.** Redstone and display link lines carry status semantics in their color and are left fully vanilla.

## Roadmap
Planned in 0.2.0: pick two gauges with shift+right-click to dye every link along the shortest path between them, with a live preview

## Gallery

All 16 dye colors, applied to gauge links:

![Every dye color applied to a connection](images/all-16-colors.png)

Crossed links stay distinguishable where they overlap:

![A crossed gauge network with color-coded links](images/crossing-network.png)

## Requirements

| | |
|---|---|
| Minecraft | 1.21.1 |
| Loader | NeoForge 21.1.x |
| Create | 6.0.0 or higher |

Client **and** server for multiplayer.

## Data & Compatibility

- Colors are stored per dimension in the world save and survive panel relocation and world reload.
- Colors sync to players on login, dimension change, and chunk load — nothing to configure.
- A small config file (`config/create_colored_connections-common.toml`) covers the optional extras: dye consumption (off by default — one dye per action, not per link), the one-time first-gauge hint, the dye feedback effects, and the hover lift.

## Roadmap

- **0.1** — single-link dyeing, color inheritance, status-preserving rendering, hover lift
- **0.2** — path dyeing with live green preview, dye/sound feedback, config file, first-placement hint
- **Next** — whatever players actually ask for: [open an issue](https://github.com/Rlosking-C/create-colored-connections/issues)

Nothing on this list is a promise; priorities follow player feedback.

## Download

Find this mod on [Modrinth](https://modrinth.com/mod/create-colored-connections), [CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-colored-connections), and [GitHub releases](https://github.com/Rlosking-C/create-colored-connections/releases).

## Modpacks

You may include this mod in any modpack, no permission needed.

## License

[MIT](LICENSE)
