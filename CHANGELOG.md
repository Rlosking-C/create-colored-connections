# Changelog

All notable changes to this project are documented in this file.

## 0.3.0

- Goggles tracing: while wearing Engineer's Goggles, resting the crosshair on a dyed link lights up its whole color group — the "read your network" tool for dense factories.
- The highlight follows the crosshair: switching to a different color (or the same color in another factory) cross-fades — the old group dims out on the fade-out curve while the new one lights up on the fade-in curve — and the highlight smoothly fades out as soon as the crosshair leaves the dyed links.
- Graying stays inside the hovered link's factory: other color groups in the same network dim to gray, while other factories render completely untouched.
- The trace readout is appended straight into Create's own goggle overlay — same position, styling and animation, no separate HUD box — as a two-level list: "In this production line:" with the link and gauge counts indented under it, then "Of which:" with the status counters indented under that.
- When every gauge in the group requests the same item, a yellow line shows the group's shortage: how many more items it still needs (target − in storage − in transit), the shopping list for restocking runs — stack mode ("stacks" row) gauges count full stacks, exactly like the game's own requests.
- Status counters hide zero values and lead with what matters: failed (red) first, then running, idle and met counts. A yellow warning (Create's own wording) appears when some stock links sit in unloaded chunks.
- Status counts match what the network is doing: links with shipments in transit are running, satisfied (green) ones are done, red restock flashes are failed, and everything else — including outstanding requests with nothing moving — is idle.
- Tracing is per-player in multiplayer: entirely client-side, no network traffic, two players can trace different colors of the same network.
- New config options: `gogglesTracing`, `traceDistance`, `traceHud`.

## 0.2.0

- Path dyeing: shift+right-click a gauge to start a path, sweep the crosshair over more gauges to extend it, then right-click the last gauge to dye every link along the route in one action (black dye resets the whole path).
- While a path is pending, a green light-beam preview floats along the exact route that will be dyed.
- Sound and dye-colored particle feedback on dyeing (configurable).
- Dyeing is free by default: holding a dye is enough, consumption is an opt-in config.
- One-time chat hint the first time you place a factory gauge.
- New config file with `dyeConsumption`, `firstGaugeHint`, `dyeEffects` and `hoverLift` options.

## 0.1.1

- Dyeing no longer consumes the dye: holding any dye is enough, and you can recolor as often as you like. The color is an organizational tag, not a crafted product.

## 0.1.0

Initial release.

- Dye Factory Gauge recipe links with any of the 16 vanilla dyes; black dye resets to the vanilla color.
- New links inherit the source gauge's color when all incoming links share a single color.
- Status-colored lines keep their vanilla core and gain a thin dye border; animations are untouched.
- Hovered links lift above their neighbors to stay distinguishable at crossings.
- Hover picking is sticky: once a line is hovered it stays picked until the crosshair clearly moves to another line, so crossing or overlapping lines no longer flip the hover on tiny mouse movements.
- Redstone and display link lines are never dyed.
