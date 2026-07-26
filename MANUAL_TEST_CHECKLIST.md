# Mimope Manual Multiplayer Test Checklist

Use this checklist before marking a build as a release candidate.

- Start backend and frontend, then open `http://localhost:5173`.
- Join as Player A with a valid nickname.
- Open a second browser tab or window and join as Player B.
- Verify both players appear and movement updates in both tabs.
- Move Player A into food and verify XP increases.
- Reach the first evolution threshold and choose Rabbit.
- Verify the sprite, radius, health, and HUD animal update after evolution.
- Move a stronger animal into a lower-tier animal and verify death screen appears for the victim.
- Click Play Again and verify respawn returns through the normal join flow.
- Left-click (or press Space/W) and verify the creature is pushed forward, the ~1.5s cooldown, and that dashing drains 5% of the water bar.
- Take a sea animal (e.g. Shrimp) out of the ocean onto land and verify the water bar empties in about four seconds, then dehydration damage starts.
- Hold left-click (or Space/W) down and verify the dash re-fires automatically every cooldown; release and verify it stops.
- Move the pointer near the centre of the screen and then far out, and verify the travel speed is identical — the pointer only sets direction.
- Drain the water bar below 10% and verify dashing is blocked until it refills.
- Toggle grid debug and verify the spatial grid overlay appears and disappears.
- Refresh one connected tab and verify the other tab remains connected.
- Send malformed WebSocket messages during development and verify server returns `error`.
- Run `node scripts/load-test.mjs` against a running backend.
