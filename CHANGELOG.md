# Changelog

All notable changes to this project are documented in this file.

## Unreleased

- Built against Create 6.0.10 — the current public release — instead of the 6.0.11 development build. Create's 6.0.10 metadata publishes its own libraries as runtime-only, so the addon now declares Ponder, Registrate and Flywheel explicitly (versions matching what the released jar bundles). Create 6.0.10 is now the minimum supported version.
- The config is split into two standard NeoForge files so in-game config editors such as Configured pick both up automatically:
  - `create_colored_connections-common.toml` — server-side flags: dye consumption, first-gauge hint, dye effects, Factory Controller dyeing;
  - `create_colored_connections-client.toml` (new) — this player's own rendering and tracing options: hover lift, goggles tracing, trace distance, trace HUD.
  Existing config files are migrated automatically; the client-only keys are removed from the common file, and their values there are replaced by the defaults in the new client file.
- Dyeing now reuses the vanilla use action: a dye click reports SUCCESS instead of only cancelling the interaction, so the client plays the vanilla arm swing and item-use feedback. The gesture still never opens the gauge panel or places a block.
- Path dyeing gestures now follow Create's own connection flow: Shift+right-click cancels a pending chain from anywhere (it used to append a gauge), a plain right-click on another gauge appends it, and a plain right-click on the start gauge — or on anything that is not a gauge, thin air included — cancels. Create cancels its own connection flow from the input layer, and so does this addon now, which is what makes a chain started while facing a wall abortable without aiming at a block.
- A pending chain is now explained the whole time it is open, instead of being announced once: the action bar keeps a live hint with the link and gauge counts and the dye in hand, and the gauge a click would confirm is boxed by a blinking outline — the same two-tone green Create uses to mark the panel its own connection started from. A gauge that refuses to join reports the concrete reason (already in the path / no connection from the chain's end) in red with Create's deny sound, and the exits a player did not ask for (dye left the hand, idle timeout) say so as well.
- The path preview's corner geometry was rebuilt. Runs used to overlap by half a beam width at every joint, which made the two boxes' shared top faces double-blend into a visibly darker square while the outer corner stayed open. Runs now butt end to end and a dedicated corner piece — covering exactly the quadrant a right-angle joint leaves open, touching both beams instead of overlapping them — closes the seam.
- Fixed the gauge config screen opening on top of the click that confirms a path. The addon accepted a dye in either hand for keeping a chain alive, but only the interacting hand's dye for handling clicks, so with the dye in the offhand the chain stayed pending while the confirm click fell through to vanilla and opened the panel screen. A pending chain now claims every right-click whatever is held, and applies the dye from whichever hand carries it (dropping the chain, with a message, when neither hand does).
- While a chain is pending, Create's gauge hover tip ("click to configure", "hold to change target amount") is silenced: it advertises an action the chain blocks, right on top of the gauge about to be clicked. Your own action-bar line and the blinking box on the confirming gauge stay.
- Preview beams are half as thick (1px instead of 2px), and no two of them share space any more: beams are drawn in a stable order and each one is cut back to what the earlier ones already painted. Where two runs of a chain crossed, the overlap used to double-blend into a darker, stepped patch; now the beams meet exactly, so a crossing reads as a clean cross.
- Where the path turns at a gauge, the preview no longer shows a cross. Each link's beam used to overshoot its gauge's slot centre by a quarter block, so the two links meeting at a corner stuck out in opposite directions and the elbow read as a "+". They now overlap by half a beam width — the same joint fill the corners use — which still leaves no notch inside the gauge but nothing poking out of the far side either.
- The action-bar line is shorter: the link and gauge counts and the dye in hand, then just the two gestures. Which gauge the click would confirm is what the blinking box says, so the longer wording was redundant.
- Dyed links on active gauges no longer show a stepped, darker patch at L-corners. The dye border is drawn per segment, and at a right-angle joint the wider band of one segment used to lie over the other's core. The band now sinks one vanilla layer step further, so at a joint it ends up below *both* cores: the vanilla line keeps its full L, the two bands alone fill the quadrant the cores leave open, and the quadrant diagonally outside the elbow stays empty.
- The dye border's shade pattern is now exactly as dense as the vanilla line's. Corner joints used to be closed with small patches, and a patch had to squeeze the line texture into a quarter of its length to fit the gap — the pattern on it read as far denser than the line it borders. Nothing is shortened anywhere now: every border segment is drawn across its whole path step at the texture's original 1:1 sampling. Create's `SuperByteBuffer.scale` moves vertices only and cannot re-sample a texture, so any length scaling squeezes the pattern rather than cropping it cleanly.
- Seven more languages: Russian, German, French, Japanese, Korean, Brazilian Portuguese and European Spanish. The wording is anchored on Create's own translations of the same content (factory gauge, redstone and display links, goggles) and on vanilla's dye and colour names, so a player reads the same terms in this addon's messages as on the blocks themselves. Keys this addon borrows from Create (`create.factory_panel.some_links_unloaded`) and from vanilla (dye items, colour names) needed no work — they already resolve in every language.
- Fixed the goggles trace readout detaching from its item icon in the longer languages: the panel sat to the left of the icon instead of under it, and a long line could wrap mid-sentence. Create measures its overlay tooltip and derives the panel anchor from that measurement, and the trace lines used to be appended only at the draw call — after the measurement — so the width Create clamped against never included them. They are now appended at the last internal guard, before anything is measured, so width, height, anchor, icon position and the fade-in all see the same tooltip.
- The action bar's path hint is down to the two gestures. The vanilla action bar is a single centred line that neither wraps nor steps aside for the screen edge, so the link and gauge counts, the dye in hand and the gestures together ran off the screen in French, Portuguese, Spanish, Russian and German — English only just fitted, and the gesture hints came off worst. The line now reads "right-click: confirm · empty space: cancel" in every language, which leaves the widest of them (German and French) at about two thirds of the width English used to occupy, so no language depends on the screen being wide. The chain's size stays readable from the growing preview beams and the boxed target gauge.

## 0.4.1

- Factory Controller compatibility (optional): when [Factory Controller](https://modrinth.com/mod/create-factory-controller) 1.2.1+ is installed, its virtual connection wires become dyeable too. Older Factory Controller versions disable the integration cleanly — wires stay vanilla, nothing crashes.
- Dye wires inside the Factory Controller GUI: pick a dye up onto the cursor and right-click a hovered logistics wire to color it — the same pick-up-and-apply gesture as on the world side (a dye in the main hand works as a fallback). Black dye clears the color, the dye is never consumed, and the client-side dyeing config option carries over.
- The GUI rendering mirrors the world side: idle wires take the dye color over their whole line, active wires keep their vanilla status core and flash animations and gain a textured 2px dye border per side — the same border-to-core proportion as a dyed wall link, reusing Factory Controller's own connection textures.
- Hovering keeps both: the dye look stays while Factory Controller's white highlight bar still marks the hovered wire.
- The wire's hover tooltip (after Factory Controller's hover delay) gains a dye line showing the applied dye, its name rendered in the dye's own color.
- Blueprint import carries colors: scanning wall gauges with a blueprint and quill keeps their link colors when the board is imported into the Factory Controller GUI. Colors also survive saving a board as a blueprint and placing that blueprint into another controller.
- Only logistics (ingredient) wires are dyeable — number and redstone wires are left fully vanilla, mirroring the world-side rule that status-semantic lines stay untouched.
- Special thanks to [flamemaster396](https://www.curseforge.com/members/flamemaster396/projects) for suggesting this feature!

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
