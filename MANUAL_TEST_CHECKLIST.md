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
- Press W or right-click and verify dash visual effect and cooldown HUD.
- Toggle grid debug and verify the spatial grid overlay appears and disappears.
- Refresh one connected tab and verify the other tab remains connected.
- Send malformed WebSocket messages during development and verify server returns `error`.
- Run `node scripts/load-test.mjs` against a running backend.
