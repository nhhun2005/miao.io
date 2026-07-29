# Mimope First Playable Release Notes

## Highlights

- Browser-based multiplayer loop with React, PixiJS, and Spring Boot WebSocket backend.
- Server-authoritative movement, food collection, XP, evolution, predation, death, and respawn flow.
- First playable animals: Mouse, Rabbit, Pig, Fox, Deer, and Lion.
- Land, ocean, and arctic biome visuals with biome-aware food spawning and movement modifiers.
- Dash on left click (or Space/W) with a 1.5-second cooldown, a 5% drinking-water cost per dash (blocked below 10% water), and HUD state. Holding the control re-dashes automatically on every cooldown.
- Drinking-water bar drains at 2% per second on dry land, but sea animals stranded out of water lose 25% per second and run dry in four seconds.
- Leaderboard, minimap, health bar, XP bar, ping display, settings placeholder, and input/FPS development diagnostics.

## Verification

- Frontend lint, build, and unit tests.
- Backend unit and WebSocket handler tests.
- Manual multiplayer checklist in `MANUAL_TEST_CHECKLIST.md`.
- Optional fake-client WebSocket load test in `scripts/load-test.mjs`.

## Known Follow-ups

- Balance animal speeds, XP thresholds, and predation rewards after real playtesting.
- Add production observability before public deployment.
- Split frontend chunks if production bundle size becomes an issue.
