# Mimope Implementation Task List

This checklist starts from the current asset-only repository and ends with a deployable `mope.io`-style multiplayer game using ReactJS, PixiJS, and Spring Boot.

---

## Phase 0: Decisions Before Coding

- [x] Confirm final game name and branding: use `Mimope` as the working name.
- [x] Confirm whether assets should be moved into `assets/` or kept in root folders: move `img/`, `skins/`, and `icons/` into `assets/`.
- [x] Confirm target player count per room: start with one room and 50 players max.
- [x] Confirm whether version 1 needs accounts: start with no accounts.
- [x] Confirm deployment target: use Docker Compose for local development.
- [x] Confirm whether to start with JSON WebSocket messages or binary messages: start with JSON WebSocket messages.

Recommended default decisions:

- Use `Mimope` as the working name.
- Move `img/`, `skins/`, and `icons/` into `assets/`.
- Start with no accounts.
- Start with one room and 50 players max.
- Start with JSON messages.
- Use Docker Compose for local development.
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      
---

## Phase 1: Repository Setup

- [x] Create `frontend/` React + TypeScript + Vite project.
- [x] Install frontend dependencies:
  - [x] `react`
  - [x] `react-dom`
  - [x] `pixi.js`
  - [x] state manager such as `zustand`
  - [x] linting and formatting tools
- [x] Create `backend/` Spring Boot project.
- [x] Add backend dependencies:
  - [x] Spring Web
  - [x] Spring WebSocket
  - [x] Spring Boot Actuator
  - [x] Validation
  - [x] Test dependencies
- [x] Add root `README.md`.
- [x] Add root `.gitignore`.
- [x] Add root `docker-compose.yml`.
- [x] Add development scripts or Makefile for common commands.
- [x] Decide and apply asset folder migration: moved `img/`, `skins/`, and `icons/` into `assets/`.
- [x] Verify both frontend and backend start independently.

---

## Phase 2: Asset Inventory And Game Data

- [x] Inventory all files in `img/`.
- [x] Inventory all files in `skins/`.
- [x] Inventory all files in `skins/arctic/`.
- [x] Inventory all files in `skins/winter/`.
- [x] Inventory all files in `skins/fullsize/`.
- [x] Identify first playable animal set:
  - [x] Mouse
  - [x] Rabbit
  - [x] Pig
  - [x] Fox
  - [x] Deer
  - [x] Lion
- [x] Identify first playable food set:
  - [x] Berry
  - [x] Banana
  - [x] Meat
  - [x] Coconut
  - [x] Watermelon
- [x] Create frontend asset manifest.
- [x] Create backend animal definitions.
- [x] Create backend food definitions.
- [x] Ensure frontend and backend use matching IDs.
- [x] Add missing placeholder assets if needed.

---

## Phase 3: Frontend Foundation

- [x] Create React app shell.
- [x] Create route or screen structure:
  - [x] Home screen
  - [x] Game screen
  - [x] Loading screen
  - [x] Death screen
- [x] Create global UI state store.
- [x] Create global game state store.
- [x] Create environment config for backend WebSocket URL.
- [x] Create reusable button, panel, and modal UI components.
- [x] Implement nickname input.
- [x] Implement start game button.
- [x] Implement basic error display.
- [x] Add responsive layout for desktop browser.

---

## Phase 4: PixiJS Rendering Prototype

- [x] Create `GameCanvas` React component.
- [x] Create `PixiGame` lifecycle class.
- [x] Initialize PixiJS application.
- [x] Mount PixiJS canvas inside React.
- [x] Destroy PixiJS app safely on unmount.
- [x] Add rendering layers:
  - [x] Background
  - [x] Terrain
  - [x] Food
  - [x] Players
  - [x] Effects
  - [x] Debug
- [x] Load selected animal textures.
- [x] Load selected food textures.
- [x] Render test background.
- [x] Render one local test animal.
- [x] Render test food objects.
- [x] Add camera following logic.
- [x] Add zoom behavior based on player size.
- [x] Add FPS debug overlay.

---

## Phase 5: Frontend Input System

- [x] Track mouse position relative to canvas center.
- [x] Convert mouse position to movement angle.
- [x] Track boost input.
- [x] Track ability input.
- [x] Add input sequence numbers.
- [x] Throttle input sending rate.
- [x] Support window blur and focus behavior.
- [x] Prevent stuck input when tab loses focus.
- [x] Prepare touch input abstraction for future mobile support.

---

## Phase 6: Backend Foundation

- [x] Create Spring Boot application entrypoint.
- [x] Add application configuration.
- [x] Add WebSocket configuration.
- [x] Create `/ws/game` WebSocket endpoint.
- [x] Create client session registry.
- [x] Create inbound message decoder.
- [x] Create outbound message encoder.
- [x] Add nickname validation.
- [x] Add basic connection lifecycle logs.
- [x] Add ping/pong support.
- [x] Add actuator health endpoint.
- [x] Add backend unit test structure.

---

## Phase 7: Shared Protocol

- [x] Define client-to-server message types:
  - [x] `join`
  - [x] `input`
  - [x] `evolve`
  - [x] `ping`
- [x] Define server-to-client message types:
  - [x] `welcome`
  - [x] `snapshot`
  - [x] `evolution_options`
  - [x] `death`
  - [x] `pong`
  - [x] `error`
- [x] Create TypeScript protocol types.
- [x] Create Java protocol DTOs.
- [x] Add protocol version field.
- [x] Add unknown message handling.
- [x] Add malformed message handling.
- [x] Add protocol tests on backend.
- [x] Add protocol parsing tests on frontend.

---

## Phase 8: Backend Game Loop

- [x] Create `GameRoom`.
- [x] Create `GameWorld`.
- [x] Create `GameLoop`.
- [x] Run simulation at fixed tick rate.
- [x] Queue player inputs per session.
- [x] Apply latest valid input each tick.
- [x] Add world bounds.
- [x] Add player spawn positions.
- [x] Add disconnect cleanup.
- [x] Broadcast snapshots at fixed rate.
- [x] Add tick duration metrics.
- [x] Add safe shutdown for game loop.

---

## Phase 9: Player Movement

- [x] Create player entity model.
- [x] Add position, velocity, radius, angle, health, XP, and animal fields.
- [x] Implement movement from input angle.
- [x] Clamp movement to world bounds.
- [x] Validate max speed server-side.
- [x] Add boost movement rules.
- [x] Send local player state in snapshots.
- [x] Render server player state on frontend.
- [x] Add interpolation between snapshots.
- [x] Add remote player rendering.
- [x] Add nickname labels.
- [x] Verify two browser tabs can see each other.

---

## Phase 10: Food System

- [x] Create food entity model.
- [x] Create food spawn service.
- [x] Spawn food by biome and weight.
- [x] Add maximum food count.
- [x] Add food despawn or replacement logic.
- [x] Add food collision detection.
- [x] Award XP when edible food is collected.
- [x] Remove collected food from world.
- [x] Include visible food in snapshots.
- [x] Render food sprites on frontend.
- [x] Add food pickup visual feedback.
- [x] Balance initial XP values.

---

## Phase 11: Spatial Grid And Visibility

- [x] Implement server-side spatial grid.
- [x] Insert players into grid each tick.
- [x] Insert food into grid.
- [x] Query nearby entities for collision.
- [x] Query visible entities per player.
- [x] Filter snapshots by viewport radius.
- [x] Add tests for spatial grid queries.
- [x] Measure snapshot size reduction.
- [x] Add debug visualization option on frontend.

---

## Phase 12: Evolution System

- [ ] Define animal tiers.
- [ ] Define XP thresholds.
- [ ] Define evolution paths.
- [ ] Send evolution options when player reaches threshold.
- [ ] Display evolution modal in React.
- [ ] Send selected evolution to backend.
- [ ] Validate evolution eligibility server-side.
- [ ] Update player animal, radius, speed, and health.
- [ ] Update frontend sprite after evolution.
- [ ] Add evolution visual effect.
- [ ] Add tests for evolution rules.

---

## Phase 13: Predation, Damage, And Death

- [ ] Define edible animal relationships.
- [ ] Add player-vs-player collision checks.
- [ ] Determine predator/prey outcomes.
- [ ] Add damage or instant-eat rules.
- [ ] Award XP for eating players.
- [ ] Handle death server-side.
- [ ] Send death message to victim.
- [ ] Remove dead player entity or mark as dead.
- [ ] Show death screen on frontend.
- [ ] Add respawn flow.
- [ ] Add kill event to snapshot.
- [ ] Add tests for predation rules.

---

## Phase 14: Abilities

- [ ] Define ability model.
- [ ] Add cooldown tracking.
- [ ] Add first simple ability, such as dash or claw.
- [ ] Validate ability use server-side.
- [ ] Apply ability effects in game loop.
- [ ] Broadcast ability events.
- [ ] Render ability effect in PixiJS.
- [ ] Show cooldown in HUD.
- [ ] Add tests for cooldown and effect rules.

---

## Phase 15: HUD And UI

- [ ] Add health bar.
- [ ] Add XP bar.
- [ ] Add current animal display.
- [ ] Add leaderboard panel.
- [ ] Add minimap.
- [ ] Add ping display.
- [ ] Add FPS display in development.
- [ ] Add settings panel.
- [ ] Add sound toggle placeholder.
- [ ] Add reconnecting state.
- [ ] Add clean disconnect handling.

---

## Phase 16: Map And Biomes

- [ ] Add terrain background rendering.
- [ ] Add land biome.
- [ ] Add water biome.
- [ ] Add arctic biome.
- [ ] Add biome boundaries.
- [ ] Add biome movement modifiers.
- [ ] Add biome-specific food.
- [ ] Add biome-specific spawn rules.
- [ ] Add river or water current visuals.
- [ ] Add healing stone or special map objects.
- [ ] Add collision for static obstacles if needed.

---

## Phase 17: Performance And Networking Polish

- [ ] Add snapshot interpolation buffer.
- [ ] Add entity object pooling on frontend.
- [ ] Avoid recreating PixiJS sprites every snapshot.
- [ ] Add dirty updates for changed entities.
- [ ] Compress or reduce snapshot payloads.
- [ ] Limit message rate from client.
- [ ] Add backend message rate limiting.
- [ ] Add server tick lag protection.
- [ ] Add metrics for snapshot size.
- [ ] Test with many simulated clients.
- [ ] Profile frontend FPS with many entities.

---

## Phase 18: Testing

- [ ] Add backend unit tests:
  - [ ] Movement
  - [ ] Collision
  - [ ] Food pickup
  - [ ] Evolution
  - [ ] Predation
  - [ ] Spatial grid
- [ ] Add backend integration tests for WebSocket join flow.
- [ ] Add frontend unit tests for protocol parsing.
- [ ] Add frontend tests for stores.
- [ ] Add manual multiplayer test checklist.
- [ ] Add load test script for fake clients.
- [ ] Test reconnect behavior.
- [ ] Test malformed messages.
- [ ] Test browser refresh while connected.
- [ ] Test two or more players in same room.

---

## Phase 19: Build And Deployment

- [ ] Add frontend production build.
- [ ] Add backend production build.
- [ ] Add Dockerfile for frontend.
- [ ] Add Dockerfile for backend.
- [ ] Add Docker Compose for local full-stack run.
- [ ] Add reverse proxy config if needed.
- [ ] Configure WebSocket proxy support.
- [ ] Add production environment variables.
- [ ] Add health checks.
- [ ] Add deployment instructions to `README.md`.
- [ ] Verify clean clone setup works.

---

## Phase 20: Release Candidate

- [ ] Run full backend test suite.
- [ ] Run full frontend test suite.
- [ ] Run lint and formatting.
- [ ] Run local full-stack smoke test.
- [ ] Test multiplayer with at least two browser sessions.
- [ ] Test death and respawn.
- [ ] Test evolution.
- [ ] Test leaderboard.
- [ ] Test asset loading after production build.
- [ ] Fix critical bugs.
- [ ] Create first playable release notes.

---

## Phase 21: Post-MVP Improvements

- [ ] Add accounts.
- [ ] Add persistent stats.
- [ ] Add skins and cosmetics.
- [ ] Add multiple rooms.
- [ ] Add matchmaking.
- [ ] Add Redis room registry.
- [ ] Add PostgreSQL persistence.
- [ ] Add mobile controls.
- [ ] Add more animals.
- [ ] Add more biomes.
- [ ] Add more abilities.
- [ ] Add admin tools.
- [ ] Add moderation tools.
- [ ] Add replay or spectator mode.

---

## Known Risks

- Realtime multiplayer is sensitive to server tick performance.
- Large snapshots can cause lag if visibility filtering is delayed.
- Client rendering can become slow if sprites are recreated every frame.
- Asset paths may break if the current root folders are moved without updating manifests.
- Game balance will require iteration after real multiplayer testing.
- Spring Boot WebSocket scaling requires sticky sessions or room-aware routing later.

---

## Suggested First Coding Sprint

The first sprint should produce a tiny playable vertical slice:

- [ ] Create frontend Vite React app.
- [ ] Create backend Spring Boot app.
- [ ] Serve `/ws/game`.
- [ ] Connect from frontend to backend.
- [ ] Join with nickname.
- [ ] Spawn one player.
- [ ] Send mouse movement input.
- [ ] Move player on server.
- [ ] Broadcast snapshots.
- [ ] Render player in PixiJS.
- [ ] Open two browser tabs and see both players move.