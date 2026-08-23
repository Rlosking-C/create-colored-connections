# Changelog

All notable changes to this project are documented in this file.

## 0.1.0

Initial release.

- Dye Factory Gauge recipe links with any of the 16 vanilla dyes; black dye resets to the vanilla color.
- New links inherit the source gauge's color when all incoming links share a single color.
- Status-colored lines keep their vanilla core and gain a thin dye border; animations are untouched.
- Hovered links lift above their neighbors to stay distinguishable at crossings.
- Hover picking is sticky: once a line is hovered it stays picked until the crosshair clearly moves to another line, so crossing or overlapping lines no longer flip the hover on tiny mouse movements.
- Redstone and display link lines are never dyed.
