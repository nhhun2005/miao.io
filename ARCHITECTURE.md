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

Players control an animal in a large 2D world. They collect food, gain XP, evolve into stronger animals, avoid predators, use abilities, and compete for survival.

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
│   ├── routes.tsx
│   └── providers.tsx
├── ui/
│   ├── HomeScreen.tsx
│   ├── GameHud.tsx
│   ├── Leaderboard.tsx
│   ├── EvolutionModal.tsx
│   └── DeathScreen.tsx
├── game/
│   ├── GameCanvas.tsx
│   ├── PixiGame.ts
│   ├── camera/
│   ├── renderer/
│   ├── entities/
│   ├── interpolation/
│   ├── input/
│   └── assets/
├── network/
│   ├── GameSocketClient.ts
│   ├── protocol.ts
│   └── messageHandlers.ts
├── state/
│   ├── gameStore.ts
│   └── uiStore.ts
└── config/
    ├── animals.ts
    ├── assets.ts
    └── constants.ts
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
7. Effects and ability visuals
8. Nameplates
9. Debug overlays

### 3.6 Asset Strategy

Existing assets include:

- `skins/`: animal sprites
- `skins/arctic/`: arctic animal variants
- `skins/winter/`: winter variants
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
- **Java 21 recommended:** Modern runtime and performance.
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
- Ability cooldowns and effects

The backend sends compact snapshots to clients at a fixed rate.

### 4.3 Backend Modules

```text
backend/src/main/java/com/mimope/server/
├── config/
│   ├── WebSocketConfig.java
│   └── GameProperties.java
├── websocket/
│   ├── GameWebSocketHandler.java
│   ├── ClientSessionRegistry.java
│   ├── InboundMessageDecoder.java
│   └── OutboundMessageEncoder.java
├── game/
│   ├── GameLoop.java
│   ├── GameRoom.java
│   ├── GameWorld.java
│   ├── TickScheduler.java
│   └── SnapshotService.java
├── world/
│   ├── WorldConfig.java
│   ├── Biome.java
│   ├── SpatialGrid.java
│   └── CollisionService.java
├── player/
│   ├── Player.java
│   ├── PlayerInput.java
│   ├── PlayerService.java
│   └── SpawnService.java
├── animal/
│   ├── AnimalType.java
│   ├── AnimalDefinition.java
│   ├── AnimalEvolutionService.java
│   └── AbilityService.java
├── food/
│   ├── FoodEntity.java
│   ├── FoodDefinition.java
│   └── FoodSpawnService.java
└── leaderboard/
    ├── LeaderboardEntry.java
    └── LeaderboardService.java
```

### 4.4 Server Game Loop

Recommended loop:

- Simulation tick: **20 ticks per second**
- Snapshot broadcast: **10 to 20 snapshots per second**
- Input messages: accepted as they arrive, applied on next tick
- Collision grid: updated every tick
- Food spawn balancing: every few ticks

Tick flow:

```text
read queued inputs
update player movement
update abilities
resolve collisions
apply food pickup
apply damage/death
apply XP/evolution
spawn/despawn world entities
build snapshots
broadcast snapshots
```

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
  "nickname": "Player"
}
```

```json
{
  "type": "input",
  "seq": 102,
  "angle": 1.57,
  "boost": false,
  "ability": false
}
```

```json
{
  "type": "evolve",
  "animal": "rabbit"
}
```

```json
{
  "type": "ping",
  "clientTime": 123456789
}
```

### 5.3 Server To Client Messages

```json
{
  "type": "welcome",
  "playerId": "p_123",
  "world": {
    "width": 12000,
    "height": 12000
  }
}
```

```json
{
  "type": "snapshot",
  "tick": 5021,
  "players": [],
  "food": [],
  "events": []
}
```

```json
{
  "type": "evolution_options",
  "options": ["rabbit", "pig"]
}
```

```json
{
  "type": "death",
  "reason": "killed_by_player",
  "killer": "Tiger"
}
```

```json
{
  "type": "pong",
  "clientTime": 123456789,
  "serverTime": 123456999
}
```

### 5.4 Snapshot Entity Shape

```json
{
  "id": "p_123",
  "kind": "player",
  "animal": "mouse",
  "x": 2500,
  "y": 4200,
  "radius": 22,
  "angle": 1.2,
  "nickname": "Player",
  "xp": 50,
  "health": 100
}
```

---

## 6. Game Data Model

### 6.1 Animal Definition

Each animal should be data-driven:

```json
{
  "id": "mouse",
  "tier": 1,
  "biome": "land",
  "radius": 20,
  "speed": 220,
  "maxHealth": 100,
  "requiredXp": 0,
  "evolvesTo": ["rabbit", "pig"],
  "canEat": ["berry"],
  "predators": ["fox"],
  "ability": null,
  "asset": "skins/mouse.png"
}
```

### 6.2 Food Definition

```json
{
  "id": "berry",
  "biome": "land",
  "radius": 8,
  "xp": 5,
  "asset": "img/rasp.png",
  "spawnWeight": 100
}
```

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

- Width: 12000
- Height: 12000
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