# Changes Applied

- Removed the Input Debug panel and its now-unused Zustand input mirror,
  renderer synchronization, React subscriptions, and CSS.
- Removed the Settings placeholder, sound toggle state/assets, settings
  controls, CSS, and stale documentation.

- Removed the production `debug_levelup` command end-to-end: backend constants
  and handler logic, world mutation API, frontend message builder/connection
  method/control, tests, comments, and documentation.
- Removed spatial-grid debugging end-to-end: inbound DTO and message type,
  handler state, snapshot field/DTO, spatial-grid export, frontend protocol and
  store state, Pixi rendering, UI toggle/styles, tests, and documentation.
- Bumped the private WebSocket protocol to version 2; removed debug fields
  rather than retaining dead compatibility payloads. Removed debug commands now
  receive the normal unknown-message error.
- Added a typed `ConcurrentLinkedQueue` command boundary for join, disconnect,
  evolve, profile-gated force-kill, and profile-gated XP grants. Commands drain
  on the game-loop thread, synchronous results use `CompletableFuture` with a
  two-second timeout, disconnect is idempotent, and no loop waits on networking.
- Made capacity check and spawn one atomic game-loop command, preventing
  concurrent joins from exceeding `maxPlayers`, creating ghost players, or
  receiving a false welcome.
- Hardened input parsing: required finite angle, stable `[-π, π]`
  normalization, finite intensity with `[0,1]` clamp, bounded integral
  sequences, and malformed-type rejection. Player input acceptance is
  synchronized latest-wins and rejects stale/duplicate sequences.
- Added defensive finite-value handling in movement, position, and angle
  mutation so invalid values cannot poison world coordinates.
- Refactored the tick pipeline into `MovementSystem`, `WaterSystem`,
  `HealthRegenerationSystem`, `FoodCollisionSystem`, `PredationSystem`,
  `EvolutionSystem`, and `WorldEventBuffer`; `GameWorld` retains authoritative
  state and orchestration. Preserved event visibility and spatial-grid rebuild
  order.
- Changed per-tick consumed-food membership from a list lookup to `HashSet`,
  preserving event order while guaranteeing a food awards XP once.
- Added input-security, command-capacity concurrency, and typed-origin property
  tests while preserving the existing gameplay/system coverage.
- Added typed `game.websocket.allowed-origin-patterns` configuration with safe
  localhost and `127.0.0.1` defaults and the
  `GAME_WEBSOCKET_ALLOWED_ORIGIN_PATTERNS` deployment override across
  application config, Compose files, `.env.example`, README, and architecture
  docs. The global wildcard default is gone.
- Pinned all frontend packages that used `latest` to their existing lockfile
  versions, synchronized the lockfile, and changed clean installs/Compose/Make
  workflows to `npm ci`.
- Rebuilt the load-test script with configurable URL/client count/duration/input
  interval/join timeout/error threshold, per-client and aggregate metrics,
  deterministic failure exit criteria, and complete socket/timer cleanup. Added
  the 10/25/50-client scenario runner without generating reports.
- Expanded `.gitignore` for Playwright, test results, coverage, and temporary
  load output; added Docker ignore files to keep generated artifacts out of
  build contexts.
- Fixed the backend production Dockerfile to use the Maven binary supplied by
  its Maven build image, avoiding the wrapper/`MAVEN_CONFIG` lifecycle failure.
- Updated README, architecture, task checklist, manual checklist, and release
  notes to match the current protocol, threading model, origins, reproducible
  installs, load testing, and gameplay balance already defined by code.
