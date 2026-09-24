Colorize the recipe-mode connection lines of Create's Factory Gauges with the 16 vanilla dyes, so that heavily crossed networks stay readable at a glance.

Features

- Right-click a link with any dye to color it. Lines are drawn on the wall between gauges — point at the line itself, not the gauge panel. By default the dye is never consumed — not even in survival mode — so recolor as often as you like (a config option can make dyeing cost one dye per action).
- Black dye = reset. Restores the vanilla status color.
- Path dyeing. Shift+right-click a gauge to start a path, then simply sweep your crosshair over further gauges: each one joins the chain, connected to the previous gauge along its own shortest route — the sweep decides the branch, so the dyed path is exactly the route you traced. While building, every pending link carries a floating green beam. Hover the last gauge again to undo it, right-click the last gauge to dye the whole chain in one action, right-click anywhere else to cancel.
- Tactile feedback. A successful dyeing plays the same sound Create uses when two gauges link, plus a small puff of dust in the applied dye's color.
- Smart inheritance. A newly created link inherits the source gauge's incoming color — but only when all incoming links share exactly one color. Mixed or uncolored inputs stay vanilla.
- Status colors stay intact. Idle lines are fully dyed; active lines (in progress / satisfied / failed / flashing) keep their vanilla status-colored core and gain a thin 1px dye border on each side. Animations like the scrolling texture and restock flashing are untouched.
- Hover lift. Looking at any link smoothly lifts the whole line above its neighbors (a few microns — only the occlusion order changes), so you can always tell which line is which where links cross or overlap.
- Sticky hover. Once a line is hovered it stays picked while the crosshair is on it; small mouse movements over crossing or overlapping lines no longer make the highlight jump between them. The dye click targets exactly the line that is lifted on screen.
- Link lines are never dyed. Redstone and display link lines carry status semantics in their color and are left fully vanilla.

Colors are stored per dimension in the world save, survive panel relocation, and sync to every player automatically — on login, dimension change, and chunk load. An optional config file covers the extras: dye consumption (off by default), the one-time first-gauge hint, the feedback effects, and the hover lift. Nothing to set up, no commands — just vanilla dye mechanics.

Requires Create 6.0.0+ on NeoForge 21.1 (Minecraft 1.21.1). Install on both client and server for multiplayer.
