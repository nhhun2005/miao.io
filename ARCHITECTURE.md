# Mimope Architecture Plan

This document defines the proposed architecture for a browser-based `mope.io`-style multiplayer game clone using:

- **Frontend:** ReactJS, PixiJS, TypeScript
- **Backend:** Spring Boot, Java
- **Realtime networking:** WebSocket
- **Assets:** Existing root folders `img/`, `skins/`, and `icons/`

The goal is to build a scalable realtime animal survival game with smooth rendering, authoritative server logic, and a clean path from prototype to production.

---

## 1. Product Scope

### 1.1 Core Gameplay

Players control an animal in a large 2D world. They collect food, gain XP, evolve into stronger animals, avoid predators, dash to escape or chase, and compete for survival.

### 1.2 Minimum Playable Version

The first complete version should include:

- Home screen with nickname input
- WebSocket connection to a game server
- One shared realtime map
- Player movement with mouse direction
- Animal rendering using PixiJS sprites
- Food spawning and collection
- XP gain and basic evolution
- Collision detection
- Death and respawn flow
- Simple leaderboard
- Basic minimap
- Server-authoritative game loop

### 1.3 Non-Goals For The First Version

These should be delayed until after the core loop is stable:

- Accounts and login
- Payments or cosmetics shop
- Mobile apps
- Anti-cheat beyond server authority
- Multiple map regions/shards
- Complex clan or party systems
- Full original game parity

---

## 2. Repository Structure

Recommended structure from the current asset-only repository:

```text
mimope/
├── ARCHITECTURE.md
├── TASKS.md
├── README.md
├── assets/
│   ├── img/
│   ├── skins/
│   └── icons/
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   ├── index.html
│   └── src/
│       ├── app/
│       ├── game/
│       ├── network/
│       ├── ui/
│       ├── assets/
│       ├── config/
│       └── main.tsx
├── backend/
│   ├── pom.xml
│   └── src/main/java/com/mimope/server/
│       ├── MimopeServerApplication.java
│       ├── config/
│       ├── websocket/
│       ├── game/
│       ├── world/
│       ├── player/
│       ├── animal/
│       ├── food/
│       └── leaderboard/
└── docker-compose.yml
```

Recommended migration:

- Move current `img/`, `skins/`, and `icons/` into `assets/`.
- Copy or symlink selected runtime assets into `frontend/public/assets/`.
- Keep source assets separate from optimized build assets.

---

## 3. Frontend Architecture

### 3.1 Technology Choices

- **ReactJS:** Menus, HUD, overlays, settings, loading screens.
- **PixiJS:** High-performance 2D rendering canvas.
- **TypeScript:** Shared type safety for client-side game models.
- **Vite:** Fast dev server and production build.
- **Zustand or Redux Toolkit:** Lightweight UI/game state bridge.
- **WebSocket API:** Realtime communication with backend.

### 3.2 Frontend Responsibilities

The frontend should handle:

- Rendering the world, animals, food, effects, water, terrain, and UI overlays.
- Collecting input from mouse, keyboard, and touch later.
- Sending player input to the server.
- Interpolating server snapshots for smooth gameplay.
- Predicting local movement only where safe.
- Displaying HUD information such as XP, animal, leaderboard, and minimap.
- Loading and caching assets.

The frontend should not decide authoritative outcomes such as XP gain, deaths, kills, or collisions.

### 3.3 Frontend Modules

```text
frontend/src/
├── app/
│   ├── App.tsx
│   └── screens/
│       ├── HomeScreen.tsx
│       ├── LoadingScreen.tsx
│       ├── GameScreen.tsx
│       ├── DeathScreen.tsx
│       └── index.ts
├── ui/
│   ├── Button.tsx
│   ├── Modal.tsx
│   ├── Panel.tsx
│   ├── ErrorBanner.tsx
│   └── index.ts
├── game/
│   ├── GameCanvas.tsx
│   ├── PixiGame.ts
│   ├── InputManager.ts
│   └── data/
│       ├── animals.ts
│       ├── foods.ts
│       ├── assets.ts
│       └── index.ts
├── network/
│   ├── GameConnection.ts
│   └── protocol.ts
├── state/
│   ├── gameStore.ts
│   ├── uiStore.ts
│   └── inputStore.ts
├── config/
│   └── env.ts
├── styles.css
└── main.tsx
```

### 3.4 React And PixiJS Boundary

React should own:

- Page layout
- Menus
- HUD
- Modals
- Settings
- Connection state

PixiJS should own:

- Canvas lifecycle
- Sprite containers
- Entity rendering
- Camera transform
- Particle/effect rendering
- Map rendering

The bridge between React and PixiJS should be a small component:

```text
GameCanvas.tsx
```

`GameCanvas` creates and destroys a `PixiGame` instance and passes input/network updates through explicit methods.

### 3.5 Rendering Layers

PixiJS should render using stable layers:

1. Background terrain
2. Biome details
3. Water and rivers
4. Food and resources
5. Static obstacles
6. Animals and players
7. Effects and dash visuals
8. Nameplates
9. Debug overlays

### 3.6 Asset Strategy

Existing assets include:

- `skins/`: animal sprites
- `skins/arctic/`: arctic biome animal sprites
- `skins/fullsize/`: larger animal sprite references
- `img/`: food, ability icons, UI images, terrain objects
- `icons/`: legacy directory icons

Recommended frontend asset manifest:

```ts
export const ASSETS = {
  animals: {
    mouse: "/assets/skins/mouse.png",
    rabbit: "/assets/skins/rabbit.png",
    fox: "/assets/skins/fox.png"
  },
  food: {
    berry: "/assets/img/rasp.png",
    banana: "/assets/img/banana.png",
    meat: "/assets/img/meat.png"
  },
  abilities: {
    claw: "/assets/img/ability_claw.png",
    dive: "/assets/img/ability_dive.png"
  }
};
```

Assets should be loaded once at game startup through PixiJS `Assets.load`.

---

## 4. Backend Architecture

### 4.1 Technology Choices

- **Spring Boot:** Application framework.
- **Spring WebSocket:** Realtime bidirectional gameplay channel.
- **Java 17 (current baseline):** Matches `backend/pom.xml` and the Docker images; a newer LTS such as Java 21 can be adopted later.
- **Maven:** Dependency and build management.
- **Optional Redis later:** Cross-instance state, matchmaking, pub/sub.
- **Optional PostgreSQL later:** Accounts, stats, persistence.

### 4.2 Backend Responsibilities

The backend should be authoritative for:

- Player sessions
- Entity IDs
- Movement validation
- World simulation
- Food spawning
- XP gain
- Evolution choices
- Collision detection
- Death and respawn
- Leaderboard
- Dash cooldown and water cost

The backend sends compact snapshots to clients at a fixed rate.

### 4.3 Backend Modules

```text
backend/src/main/java/com/mimope/server/
├── MimopeServerApplication.java
├── HealthController.java
├── websocket/
│   ├── WebSocketConfig.java
│   ├── GameWebSocketHandler.java
│   ├── SessionRegistry.java
│   ├── ClientSession.java
│   ├── InboundMessage.java
│   ├── MessageDecoder.java
│   ├── MessageEncoder.java
│   └── NicknameValidator.java
├── game/
│   ├── GameLoop.java
│   ├── GameRoom.java
│   ├── GameWorld.java
│   ├── PlayerEntity.java
│   ├── FoodEntity.java
│   ├── FoodSpawnService.java
│   ├── SpatialGrid.java
│   ├── SnapshotMetrics.java
│   ├── DashEvent.java
│   ├── DeathEvent.java
│   ├── FoodPickupEvent.java
│   └── data/
│       ├── AnimalDefinition.java
│       ├── FoodDefinition.java
│       └── Biome.java
└── protocol/
    ├── ProtocolConstants.java
    ├── inbound/
    │   ├── JoinMessage.java
    │   ├── InputMessage.java
    │   ├── EvolveMessage.java
    │   └── PingMessage.java
    └── outbound/
        ├── WelcomeMessage.java
        ├── SnapshotMessage.java
        ├── EvolutionOptionsMessage.java
        ├── DeathMessage.java
        ├── PongMessage.java
        └── ErrorMessage.java
```

`GameWorld` owns authoritative collections and orchestrates focused movement,
water, regeneration, food-collision, predation, and evolution systems.
`WorldEventBuffer` owns the one-tick event lifetime. External join, remove,
evolve, force-kill, and test-support XP mutations are typed commands drained
by the game-loop thread; movement remains a thread-safe latest-wins input.
`GameRoom` handles bounded command waits and snapshot/network orchestration.

### 4.4 Server Game Loop

Recommended loop:

- Simulation tick: **20 ticks per second**
- Snapshot broadcast: **10 to 20 snapshots per second**
- Input messages: accepted as they arrive, applied on next tick
- Collision grid: updated every tick
- Food spawn balancing: every few ticks

Tick flow:

```text
clear tick event buffer
drain typed world commands
resolve retained movement and dash
update water and health regeneration
rebuild spatial grid
resolve food collision and predation
emit evolution options
replenish food
rebuild spatial grid for snapshot visibility
broadcast filtered snapshots
```

WebSocket origin patterns are bound from
`game.websocket.allowed-origin-patterns`. Localhost and `127.0.0.1` are the
safe defaults; deployment adds its public frontend origin through
`GAME_WEBSOCKET_ALLOWED_ORIGIN_PATTERNS`.

### 4.5 Spatial Partitioning

Use a uniform spatial grid for collision and visibility queries.

Benefits:

- Avoids O(n²) collision checks.
- Supports viewport-based snapshot filtering.
- Makes large maps practical.

Core queries:

- Get nearby food for a player.
- Get nearby players for collision.
- Get visible entities for a client snapshot.
- Get nearby obstacles and biome objects.

---

## 5. Realtime Protocol

### 5.1 Transport

Use WebSocket endpoint:

```text
/ws/game
```

During development, JSON messages are acceptable. For production, switch to a compact binary protocol such as MessagePack or custom byte buffers.

### 5.2 Client To Server Messages

```json
{
  "type": "join",
  "nickname": "Player",
  "starterAnimalId": "mouse"
}
```

`starterAnimalId` is optional; when omitted or invalid the server falls back to
the default starter. Valid starters are `mouse`, `shrimp`, and `chipmunk`.

```json
{
  "type": "input",
  "seq": 102,
  "angle": 1.57,
  "intensity": 1.0,
  "dash": false,
  "timestamp": 123456789
}
```

```json
{
  "type": "evolve",
  "animalId": "rabbit"
}
```

```json
{
  "type": "ping",
  "timestamp": 123456789
}
```

Production protocol messages cannot mutate XP for debugging or request
spatial-grid visualization. Profile-gated test-support endpoints provide
deterministic E2E setup without exposing those capabilities in production.

### 5.3 Server To Client Messages

```json
{
  "type": "welcome",
  "playerId": "p_123",
  "nickname": "Player",
  "protocolVersion": 2
}
```

```json
{
  "type": "snapshot",
  "tick": 5021,
  "players": [],
  "foods": [],
  "leaderboard": [],
  "foodPickups": [],
  "killEvents": [],
  "dashEvents": []
}
```

The event arrays (`foodPickups`, `killEvents`, `dashEvents`) are
omitted from the payload when empty.

```json
{
  "type": "evolution_options",
  "options": [
    { "animalId": "rabbit", "name": "Rabbit", "tier": 2 },
    { "animalId": "pig", "name": "Pig", "tier": 4 }
  ]
}
```

```json
{
  "type": "death",
  "reason": "eaten",
  "killerNickname": "Tiger",
  "xpEarned": 450,
  "survivalTimeMs": 120000
}
```

```json
{
  "type": "pong",
  "timestamp": 123456789
}
```

### 5.4 Snapshot Entity Shapes

Player entry (`players[]`):

```json
{
  "id": "p_123",
  "nickname": "Player",
  "x": 2500,
  "y": 4200,
  "radius": 22,
  "angle": 1.2,
  "animalId": "mouse",
  "health": 2,
  "maxHealth": 2,
  "xp": 50,
  "oceanSurvival": 100,
  "maxOceanSurvival": 100,
  "dashCooldownTicks": 0
}
```

Food entry (`foods[]`):

```json
{
  "id": "f_42",
  "foodId": "berry",
  "x": 1800,
  "y": 3200
}
```

---

## 6. Game Data Model

### 6.1 Animal Definition

Each animal should be data-driven:

```json
{
  "id": "mouse",
  "name": "Mouse",
  "tier": 1,
  "biome": "LAND",
  "radius": 22,
  "speed": 200,
  "maxHealth": 2,
  "xpRequired": 0,
  "normalEvolution": true
}
```

`maxHealth` is derived from tier (see `AnimalDefinition.maxHealthForTier`).
Evolution targets are resolved by tier/biome at runtime rather than stored as an
`evolvesTo` list, and predation is decided server-side by relative size, so
there are no `evolvesTo`, `canEat`, or `predators` fields on the definition.

### 6.2 Food Definition

```json
{
  "id": "berry",
  "name": "Berry",
  "biome": "LAND",
  "radius": 10,
  "xp": 5,
  "minTier": 1,
  "spawnWeight": 50
}
```

`minTier` is the minimum animal tier required to eat the food. Asset paths live
in the frontend food registry rather than on the server-side definition.

### 6.3 Runtime Entities

Core runtime entity fields:

- `id`
- `type`
- `position`
- `velocity`
- `radius`
- `biome`
- `createdAt`
- `updatedAt`

---

## 7. World Design

### 7.1 Map

Start with one large rectangular map:

- Width: 5000
- Height: 5000
- Spawn-safe central area
- Land biome first
- Add water, ocean, arctic, and desert later

### 7.2 Biomes

Initial biomes:

- Land
- Water
- Arctic

Each biome controls:

- Food types
- Movement modifiers
- Animal eligibility
- Visual background
- Spawn rules

### 7.3 Collision Rules

Initial rules:

- Players collide softly with obstacles.
- Players can collect edible food.
- Larger animals can damage or eat smaller animals.
- Same-tier animals should not instantly kill each other.
- Server decides all collision outcomes.

---

## 8. Scalability Plan

### 8.1 Phase 1: Single Server

One Spring Boot process manages one room.

Suitable for:

- Local development
- Small test sessions
- Gameplay validation

### 8.2 Phase 2: Multiple Rooms

One process manages multiple `GameRoom` instances.

Add:

- Room capacity
- Matchmaking
- Room list
- Per-room game loops

### 8.3 Phase 3: Horizontal Scaling

Multiple backend instances behind a load balancer.

Add:

- Sticky WebSocket sessions
- Redis for room discovery
- Dedicated game-room ownership
- Metrics and autoscaling

---

## 9. Security And Fair Play

Minimum safeguards:

- Server-authoritative movement speed.
- Ignore impossible input values.
- Rate-limit messages per connection.
- Sanitize nicknames.
- Disconnect idle or malformed clients.
- Do not trust client XP, health, position, or animal state.
- Validate evolution eligibility server-side.

---

## 10. Observability

Backend should expose:

- Active connections
- Tick duration
- Snapshot size
- Messages per second
- Room player count
- JVM memory
- Error rate

Recommended tools:

- Spring Boot Actuator
- Micrometer
- Prometheus later
- Grafana later

Frontend should expose development overlays:

- FPS
- Ping
- Snapshot delay
- Entity count
- Interpolation buffer size

---

## 11. Development Milestones

1. Asset inventory and manifests
2. Frontend PixiJS rendering prototype
3. Spring Boot WebSocket echo prototype
4. Join game and render local player
5. Server-authoritative movement
6. Food spawning and collection
7. XP and evolution
8. Collision and death
9. Leaderboard and HUD
10. Polish, optimization, and deployment

---

## 12. Open Decisions

These decisions should be confirmed before full implementation:

- Exact game name and branding.
- Whether to move assets into `assets/` or keep current root folders.
- Whether the first version needs accounts.
- Target max players per room.
- Whether deployment target is Docker VPS, cloud platform, or local-only.
- Whether to use JSON protocol first or start with binary protocol.

Recommended defaults:

- Use no accounts for version 1.
- Use JSON protocol for the first prototype.
- Target 50 players per room initially.
- Use Docker Compose for local development.
- Keep gameplay server-authoritative from the beginning.
