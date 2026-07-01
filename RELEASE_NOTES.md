# Mimope First Playable Release Notes

## Highlights

- Browser-based multiplayer loop with React, PixiJS, and Spring Boot WebSocket backend.
- Server-authoritative movement, food collection, XP, evolution, predation, death, and respawn flow.
- First playable animals: Mouse, Rabbit, Pig, Fox, Deer, and Lion.
- Land, ocean, and arctic biome visuals with biome-aware food spawning and movement modifiers.
- Dash ability with cooldown, visual feedback, and HUD state.
- Leaderboard, minimap, health bar, XP bar, ping display, settings placeholder, and debug overlays.

## Verification

- Frontend lint, build, and unit tests.
- Backend unit and WebSocket handler tests.
- Manual multiplayer checklist in `MANUAL_TEST_CHECKLIST.md`.
- Optional fake-client WebSocket load test in `scripts/load-test.mjs`.

## Known Follow-ups

- Balance animal speeds, XP thresholds, and predation rewards after real playtesting.
- Add production observability and stricter origin rules before public deployment.
- Split frontend chunks if production bundle size becomes an issue.
