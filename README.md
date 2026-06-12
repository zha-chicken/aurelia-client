# Aurelia

Aurelia is a compact, elegant visual utility client for Minecraft Java 1.21.1
on Fabric. It reads client-visible game state and presents it clearly; it does
not automate combat, aim, attacks, crystal placement, or anti-cheat evasion.

## Features

- Animated category-based ClickGUI (`Right Shift`) with persistent toggles
- Minimal watermark, FPS, and coordinate HUD
- Animated enabled-module HUD list
- Nearby-player threat card based on distance, health, armor, and line of sight
- Nearby end-crystal risk indicator based on predicted damage and remaining health
- Translucent red crystal boxes and a green legal-placement preview
- Distance-scaled per-crystal damage/remaining-health labels, lethal markers, and a lethal-damage screen warning
- Reduced Crystal FX removes the large crystal explosion emitter while retaining feedback
- Crystal damage model includes exposure, difficulty, armor toughness, protection,
  blast protection, resistance, and absorption health
- Hold `Z` to show a green/yellow/red legal-placement heatmap
- Scaffold Guide placement-readiness indicator (does not place blocks)
- Hold `Z` for target highlighting, attack readiness, and crystal previews
- **Killaura** (Combat category): auto-target + attack nearby living entities (players/mobs/test dummy) with configurable range and visible rotation lock. Red target box overlay when active.

In the ClickGUI, left click toggles a module and right click cycles its
available setting. Settings and enabled states are saved to
`config/aurelia.json`.

- Hold `Z` to show manual combat suggestions and world highlights
- Press `H` in single-player to spawn or remove an Aurelia test dummy

Crystal damage, self-damage, and kill likelihood are conservative client-side
predictions. Server difficulty, latency, effects, enchantments, and plugins can
change the final result.

The heatmap checks obsidian/bedrock bases, free headroom, and entity occupancy.
It is capped at 24 positions and recalculated at most every four ticks.

## Build

Requires Java 21.

```bash
./gradlew build
```

The distributable jar is written to `build/libs`.

## Attribution and license

Licensed under GPL-3.0-only. The module-oriented UX and architecture are
inspired by [Meteor Client](https://github.com/MeteorDevelopment/meteor-client),
which is also GPL-3.0. No Meteor source code is included in this initial
version; see `NOTICE`.
