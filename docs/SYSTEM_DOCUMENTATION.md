# Tài liệu hệ thống Mimope.io (As-built)

> Phiên bản tài liệu: 2026-07-29  
> Phạm vi khảo sát: toàn bộ mã nguồn `backend/src/main`, `frontend/src`, cấu
> hình build/deploy, script vận hành và test hiện có. Đây là mô tả hệ thống
> đang chạy, không phải kiến trúc dự kiến.

## 1. Tổng quan

Mimope.io là game sinh tồn nhiều người chơi thời gian thực trên trình duyệt.
Người chơi điều khiển một sinh vật, ăn thức ăn để nhận XP, tiến hóa qua 15
tầng, duy trì máu/nước, dash, săn hoặc né người chơi khác và hoạt động trong
ba biome LAND, OCEAN, ARCTIC.

Kiến trúc chia hai tiến trình:

- Client React/TypeScript quản lý màn hình và HUD; PixiJS render thế giới.
- Server Spring Boot/Java giữ trạng thái có thẩm quyền, chạy game loop 20 Hz,
  xử lý va chạm và phát snapshot qua WebSocket.

Server là nguồn sự thật duy nhất đối với vị trí, XP, máu, tiến hóa, thức ăn,
dash, tử vong và bảng xếp hạng. Client chỉ thu input, gửi ý định và nội suy dữ
liệu server để hình ảnh mượt hơn.

## 2. Công nghệ và kỹ thuật

| Khu vực | Công nghệ/kỹ thuật | Vai trò |
|---|---|---|
| UI | React 19, Zustand | Luồng màn hình, HUD, state bridge |
| Render | PixiJS 8, pixi-filters | Sprite, camera, biome, hiệu ứng, contour |
| Client | TypeScript, Vite | Type safety, build/dev server |
| Realtime | WebSocket JSON protocol v2 | Input lên server, snapshot/event xuống |
| Server | Java 17, Spring Boot 3 | DI, lifecycle, WebSocket, actuator |
| Simulation | Fixed-rate authoritative loop | Tick độc lập client, chống gian lận cơ bản |
| Concurrency | Single-writer command queue | Mutation thế giới trên game-loop thread |
| Spatial | Uniform spatial grid | Giảm chi phí collision và lọc viewport |
| Testing | JUnit 5, Mockito, Vitest, Playwright | Unit, integration, E2E |
| Deploy | Docker multi-stage, nginx, Compose | Build và chạy development/production |
| Operations | Actuator, snapshot/tick metrics, load scripts | Health, quan sát tải |

Các mẫu thiết kế đáng chú ý:

- **Authoritative server:** client không tự quyết định kết quả gameplay.
- **Game loop + systems:** `GameWorld` điều phối các system nhỏ theo thứ tự.
- **Command pattern:** `GameCommand` biểu diễn mutation đi qua
  `GameCommandQueue`.
- **Data-oriented snapshot:** DTO record bất biến, JSON có type discriminator.
- **Object pooling:** sprite thức ăn được tái sử dụng ở client.
- **Observer/callback:** `GameConnection` chuyển snapshot cho store và Pixi.
- **State machine:** `uiStore` điều khiển home → loading → game → death.
- **Viewport interest management:** mỗi client chỉ nhận entity gần mình.
- **Latest-wins input:** sequence loại input cũ/trùng.

## 3. Cấu trúc triển khai

```text
Browser
├─ React UI + Zustand
├─ GameConnection (WebSocket)
└─ PixiGame + InputManager
          │ JSON/WebSocket /ws
          ▼
Spring Boot
├─ GameWebSocketHandler
├─ SessionRegistry / codec / validation
├─ GameRoom + GameLoop
└─ GameWorld
   ├─ command queue
   ├─ gameplay systems
   ├─ player/food entities
   └─ spatial grid + event buffer
```

Mặc định:

- Frontend development: `http://localhost:5173`.
- Backend: `http://localhost:8080`.
- WebSocket: `/ws`.
- Health endpoint: `/actuator/health`.
- World: 5000 × 5000, tối đa 50 player và 200 food, tick rate 20 Hz.

## 4. Mô hình lớp backend

### 4.1 Bootstrap và HTTP

| Lớp | Trách nhiệm |
|---|---|
| `MimopeServerApplication` | Entry point Spring Boot. |
| `HealthController` | Endpoint đơn giản xác nhận service hoạt động. |
| `TestSupportController` | API hỗ trợ E2E khi profile `test-support` bật; grant XP/force kill, không thuộc production mặc định. |

### 4.2 WebSocket

| Lớp | Trách nhiệm và quan hệ |
|---|---|
| `WebSocketConfig` | Đăng ký `GameWebSocketHandler` tại `/ws`, áp origin patterns. |
| `WebSocketProperties` | Binding cấu hình `game.websocket.allowed-origin-patterns`. |
| `GameWebSocketHandler` | Lifecycle socket; rate-limit; decode và dispatch `join`, `input`, `evolve`, `ping`; gửi lỗi; gọi `GameRoom`. |
| `ClientSession` | Bọc `WebSocketSession`; nickname, thời điểm kết nối/pong, trạng thái open và token-window rate limit. |
| `SessionRegistry` | Registry thread-safe theo session ID. |
| `InboundMessage` | Envelope đã decode: `type`, `protocolVersion`, `payload`; accessor có kiểm tra kiểu. |
| `MessageDecoder` | Jackson JSON → `InboundMessage`; từ chối JSON sai cấu trúc. |
| `MessageEncoder` | Serialize và gửi message thread-safe; xử lý socket đóng/lỗi gửi. |
| `NicknameValidator` | Trim và kiểm tra nickname bắt buộc, độ dài, ký tự hợp lệ. |

Luồng dispatch:

1. Socket mới được bọc thành `ClientSession` và đưa vào registry.
2. Mỗi text frame qua rate limit, decoder và kiểm tra protocol version.
3. `join` xác thực nickname/starter rồi gọi command join đồng bộ có timeout.
4. `input` được parse nghiêm ngặt và xếp latest-wins.
5. `evolve` tạo command chờ kết quả.
6. `ping` trả `pong`.
7. Close/error xóa session và xếp command remove idempotent.

### 4.3 Orchestration và concurrency

| Lớp | Trách nhiệm và quan hệ |
|---|---|
| `GameRoom` | Component sở hữu một `GameWorld`, một `GameLoop`; quản lý join/leave/evolve; tạo leaderboard và snapshot riêng cho từng client. |
| `GameLoop` | Thread daemon chạy fixed-rate; gọi `world.tick(dt)`, rồi callback broadcast; ghi duration metrics và bù nhịp. |
| `GameCommand` | Sealed command: `Join`, `Remove`, `Evolve`, `ForceKill`, `GrantXp`. |
| `GameCommandQueue` | `ConcurrentLinkedQueue`; drain FIFO trên game-loop thread. |
| `SnapshotMetrics` | Đếm kích thước snapshot filtered/unfiltered và log tỷ lệ tiết kiệm. |

Mutation bên ngoài simulation không được sửa trực tiếp collections của world.
Command join/evolve/test-support dùng `CompletableFuture`; caller chờ tối đa hai
giây. Capacity check và spawn cùng nằm trong command join nên không có race
vượt `maxPlayers`.

### 4.4 Domain và simulation

| Lớp | Trách nhiệm chính |
|---|---|
| `GameWorld` | Aggregate authoritative: player, food, tick, biome geometry, cooldown cắn, grid, command queue, systems và events. |
| `PlayerEntity` | Trạng thái sinh vật; input sequence, movement, dash, XP, máu, nước, regen, evolution eligibility và alive state. |
| `FoodEntity` | Instance food trong world, tham chiếu `FoodDefinition`. |
| `FoodSpawnService` | Bổ sung food theo trọng số và biome đến giới hạn. |
| `SpatialGrid` | Chia world thành cell; insert/query player và food theo vùng tròn. |
| `MovementSystem` | Consume input, kích hoạt/advance dash, áp multiplier biome và movement. |
| `WaterSystem` | Refill/tiêu hao nước và phát death event khi dehydration. |
| `HealthRegenerationSystem` | Hồi máu sau khoảng không nhận damage. |
| `FoodCollisionSystem` | Query grid, kiểm tra tier/radius, cộng XP, refill nước, xóa food và phát pickup. |
| `PredationSystem` | Adapter gọi resolver predation của world. |
| `EvolutionSystem` | Phát lựa chọn tiến hóa một lần khi đủ XP. |
| `WorldEventBuffer` | Danh sách event chỉ sống trong một tick. |

Các record/event:

- `FoodPickupEvent`: food, tọa độ, XP và player nhận.
- `DeathEvent`: victim/killer/reason/tọa độ/XP/thời gian sống.
- `DashEvent`: player/tọa độ/góc lúc dash.
- `GameWorld.EvolutionOptionsEvent`: player và lựa chọn.
- `GameWorld.EvolutionResult`: success/failure + player/error.
- `GameWorld.SpawnPoint`, `Puddle`: value objects hình học.

Các kiểu nội bộ hỗ trợ thuật toán:

- `GameWorld.PendingKnockback`: giữ knockback để áp sau khi resolve mọi bite.
- `GameWorld.BiteResult`: kết quả hit nội bộ (`killed`, `stolenXp`).
- `SpatialGrid.NearbyQueryResult`: cặp danh sách player/food sau spatial query.
- `SpatialGrid.Cell`: bucket nội bộ chứa entity overlap cell.
- `PredationSystem.Resolver`: functional interface nối stage system với resolver
  giữ authoritative state trong world.
- `GameRoom.GameCommandTimeoutException`: báo command synchronous vượt timeout.

### 4.5 Data registry

| Kiểu | Nội dung |
|---|---|
| `AnimalDefinition` | Registry động vật: id, name, tier, speed, radius, health, XP threshold, biome, normal evolution. |
| `FoodDefinition` | Registry food: XP, radius, minimum tier, biome, spawn weight. |
| `Biome` | `LAND`, `OCEAN`, `ARCTIC`, `FINAL`; mapping protocol lowercase. |

Registry hiện có 15 tier. Tier 1 có ba starter theo biome; tier 15
`blackdragon` thuộc `FINAL`, không bị ép chuyển biome và không chịu penalty
biome.

### 4.6 Protocol DTO

Inbound records:

- `JoinMessage(nickname, starterAnimalId)`
- `InputMessage(seq, angle, intensity, dash)`
- `EvolveMessage(animalId)`
- `PingMessage(timestamp)`

Outbound records:

- `WelcomeMessage`: protocol version, player identity, world dimensions.
- `SnapshotMessage`: tick, visible players/foods, leaderboard và pickup/kill/dash events.
- `EvolutionOptionsMessage`: danh sách lựa chọn tier kế.
- `DeathMessage`: reason, killer, lifetime và XP.
- `PongMessage`, `ErrorMessage`.

`SnapshotMessage` chứa các record con `PlayerData`, `FoodData`,
`LeaderboardEntry`, `FoodPickupData`, `KillEventData`, `DashEventData`. Mỗi
record có `toMap()` để giữ shape JSON ổn định và bỏ các event list rỗng khỏi
payload. `EvolutionOptionsMessage.EvolutionOption` biểu diễn một lựa chọn gồm
animal ID, tên hiển thị và tier.

`ProtocolConstants` tập trung version và tên message. Client/server cùng dùng
protocol v2, nhưng type được khai báo riêng ở Java và TypeScript; script
`check-id-consistency.sh` kiểm tra ID registry giữa hai phía.

## 5. Mô hình module frontend

### 5.1 React và store

| Module | Trách nhiệm |
|---|---|
| `main.tsx` | Mount `App` trong `StrictMode`. |
| `App` | Chọn screen từ `uiStore`, luôn render `ErrorBanner`. |
| `HomeScreen` | Nhập nickname, chọn starter, chuyển sang loading. |
| `LoadingScreen` | Tạo connection, connect/join, giữ instance qua chuyển screen. |
| `GameScreen` | Canvas, HP/XP/dash HUD, leaderboard, minimap, ping và evolution modal. |
| `DeathScreen` | Hiện nguyên nhân chết; reset để chơi lại. |
| `uiStore` | Screen, nickname, starter, error, death message. |
| `gameStore` | Local ID, visible players/foods, leaderboard, tick, evolution options. |
| `Button`, `Panel`, `Modal`, `ErrorBanner` | UI primitives. |

### 5.2 Network

| Module | Trách nhiệm |
|---|---|
| `protocol.ts` | Enum type, interface DTO, builders và runtime parser/validator. |
| `GameConnection` | WebSocket lifecycle, ping 5 giây, latency, reconnect tối đa 3 lần, dispatch message vào store/Pixi. |
| `env.ts` | Suy ra WebSocket URL từ `VITE_WS_URL` hoặc location hiện tại. |

Reconnect dùng delay tuyến tính `500ms × attempt`. Sau death, connection đánh
dấu expected close và không reconnect. Nếu hết ba lần, client về home và báo
lỗi.

### 5.3 Input và rendering

| Module/lớp | Trách nhiệm |
|---|---|
| `GameCanvas` | React–Pixi bridge; tạo/hủy `PixiGame`, gắn snapshot callback. |
| `InputManager` | Pointer/touch/keyboard; angle, intensity, dash; sequence tăng đơn điệu; throttle 20 Hz; xử lý blur. |
| `PixiGame` | Pixi lifecycle, asset cache, layers, camera/zoom, snapshot-to-sprite, interpolation, HUD FPS, map/biome, effects và contour. |
| `animals.ts` | Client registry, evolution helper và preview path. |
| `foods.ts` | Client food registry. |
| `assets.ts` | Asset keys và manifest từ thư mục `assets/`. |

Các layer Pixi:

1. Background/biome geometry.
2. Food.
3. Player.
4. Effects.

Mỗi snapshot cập nhật target state. Mỗi render tick nội suy `displayX`,
`displayY`, góc và radius về target để tránh giật ở snapshot rate 20 Hz. Camera
bám local player và zoom phụ thuộc kích thước sinh vật.

Food texture nguồn có keyline màu; client tạo texture đã erosion alpha một lần
khi load. Food ăn được không có filter, food chưa đủ tier có black
`OutlineFilter`. Player vẫn dùng neutral slate hoặc pulsing red khi là predator.

## 6. Luồng hoạt động

### 6.1 Khởi động

1. Spring tạo registry, codec, handler, metrics và `GameRoom`.
2. `GameRoom.init()` tạo world/loop rồi start thread.
3. Vite/nginx phục vụ bundle và assets.
4. React mount `App`, mặc định ở home.

### 6.2 Tham gia game

1. Người chơi nhập nickname và chọn mouse/shrimp/chipmunk.
2. Home lưu Zustand rồi chuyển loading.
3. Loading mở `/ws`; khi open gửi `join`.
4. Server validate protocol, rate limit, nickname và starter.
5. `GameRoom` enqueue `Join`; game loop atomic check capacity + spawn.
6. Handler gửi `welcome`; client lưu player ID và chuyển game.
7. Pixi load assets, tạo world và bắt đầu render/input.

### 6.3 Một tick authoritative

Theo đúng thứ tự trong `GameWorld.tick`:

1. Tăng tick, clear event tick trước.
2. Drain command queue.
3. Movement/dash với multiplier biome.
4. Water/refill/dehydration.
5. Health regeneration.
6. Rebuild spatial grid.
7. Food collision và XP.
8. Player predation.
9. Tạo evolution option events.
10. Replenish food.
11. Rebuild grid lần hai cho snapshot nhất quán.
12. `GameRoom` gửi evolution/death message rồi snapshot filtered.

### 6.4 Movement và biome

- Input angle phải finite và được normalize về `[-π, π]`.
- Intensity finite, clamp `[0,1]`; sequence nguyên và có giới hạn.
- Player giữ input mới nhất; input stale/trùng bị bỏ.
- Tốc độ = animal speed × intensity × dash multiplier × biome multiplier.
- Đúng biome gốc: `1.0x`; sai biome: `0.85x`; animal `FINAL`: `1.0x`.
- Vị trí luôn clamp trong world và bảo vệ khỏi NaN/Infinity.

### 6.5 Dash

- Client yêu cầu qua click, Space, W, Enter hoặc touch.
- Server kiểm tra cooldown; dash hợp lệ tạo burst `3x` trong số tick cố định.
- Event dash được gửi trong snapshot để client render hiệu ứng.
- Giữ nút có thể tự kích hoạt lại khi cooldown kết thúc.

### 6.6 Nước và hồi máu

- Nguồn nước gồm ocean, river và puddle.
- Aquatic animal có logic water/beached riêng trong `PlayerEntity`.
- Food refill một phần nước.
- Hết nước gây damage/death dehydration.
- Damage reset thời gian regen; tránh damage đủ lâu sẽ hồi máu dần.

### 6.7 Ăn food

1. Grid tìm food gần player.
2. Kiểm tra player sống, tier ≥ `minTier` và circle overlap.
3. Dùng `HashSet` đảm bảo một food chỉ được thưởng một lần/tick.
4. Cộng XP, refill water, remove food, phát pickup event.
5. Spawn service bổ sung food theo biome và weighted random.

### 6.8 Predation

- Không cắn bản thân, player chết hoặc cùng tier.
- Hai hình tròn phải chạm và attacker phải hướng vào target.
- Sinh vật thấp tier chỉ counter-attack sinh vật cao tier ở rear arc.
- Cooldown theo cặp attacker→target ngăn damage mỗi tick.
- Mỗi hit lấy `floor(10%)`, tối thiểu 1 XP nếu target còn XP.
- Hit chí mạng chuyển toàn bộ XP còn lại, kill target và phát death event.
- Knockback bằng `0.75` quãng dash attacker, được hoãn đến khi resolve toàn bộ
  bite trong tick để hỗ trợ va chạm đồng thời.

### 6.9 Tiến hóa

1. Khi đủ threshold tier kế, system phát options đúng một lần.
2. Client mở modal và gửi animal ID.
3. Command evolve kiểm tra target tồn tại, đúng tier/options và đủ XP.
4. Cập nhật definition/radius/health và reset trạng thái liên quan.
5. Nếu target không phải FINAL, player được chuyển về biome của target.
6. Snapshot tiếp theo làm client đổi texture và chạy evolution effect.

### 6.10 Snapshot/render

- Leaderboard top 10 được dựng một lần/tick.
- Entity list được lọc theo viewport của từng player qua `SpatialGrid`.
- Self luôn được đưa vào snapshot.
- Store cập nhật cho React HUD; callback đồng thời cập nhật Pixi scene.
- Sprite biến mất được pool/remove; effects có TTL.
- Client không extrapolate kết quả gameplay.

### 6.11 Death và respawn

1. Dehydration hoặc predation tạo `DeathEvent`.
2. `GameRoom` gửi `death` riêng cho victim.
3. Client chuyển death screen và coi socket close là bình thường.
4. Người chơi chọn chơi lại; store reset và bắt đầu connection/join mới.

## 7. Use cases hiện có

Use case sản phẩm chỉ mô tả mục tiêu mà actor thực hiện trực tiếp. Chi tiết
WebSocket/reconnect, thao tác nội bộ server và API hỗ trợ kiểm thử không phải
use case chính.

| ID | Actor | Use case cấp cao | Use case con/kết quả |
|---|---|---|---|
| UC-01 | Player | Tham gia trò chơi | Bao gồm nhập nickname và chọn sinh vật khởi đầu |
| UC-02 | Player | Điều khiển sinh vật | Bao gồm di chuyển; dash là hành vi mở rộng khi cooldown cho phép |
| UC-03 | Player | Sinh tồn | Bao gồm ăn thức ăn và bổ sung nước |
| UC-04 | Player | Chiến đấu với người chơi khác | Bao gồm cắn; phản công mở rộng khi player cấp thấp ở phía sau kẻ săn mồi |
| UC-05 | Player | Tiến hóa | Chọn sinh vật hợp lệ ở tier tiếp theo |
| UC-06 | Player | Theo dõi trạng thái trận đấu | Bao gồm HUD, bảng xếp hạng và bản đồ nhỏ |
| UC-07 | Player | Chơi lại sau khi chết | Reset store và bắt đầu phiên join mới |
| UC-08 | Operator | Kiểm tra trạng thái máy chủ | Xác nhận backend đang hoạt động |

Ngoại lệ chính:

- Nickname/starter/message sai → `error`.
- Room đầy → join thất bại.
- Protocol version không khớp → từ chối.
- Input stale/malformed → không tác động world.
- Evolve không hợp lệ/thiếu XP → error, giữ animal cũ.
- Mất mạng quá ba lần → trở về home.

## 8. Bảo mật và tính đúng đắn

- Origin WebSocket allowlist; mặc định chỉ localhost/127.0.0.1.
- Rate limit trên từng `ClientSession`.
- Runtime parsing cả client và server; không tin payload.
- Server authority và clamp finite ngăn client gửi tọa độ/XP tùy ý.
- Single-writer loop tránh concurrent mutation.
- Profile-gated test endpoints không bật mặc định.
- Docker/nginx chỉ expose surface cần thiết.
- Chưa có account/authentication, persistence, TLS termination hay distributed
  room; đây là giới hạn hiện tại, không nên hiểu session ID là danh tính bền vững.

## 9. Kiểm thử

Backend có 245 test tại thời điểm tài liệu được lập:

- Entity/movement/water/dash/regen/evolution.
- Collision, predation, knockback, XP transfer và biome.
- Spatial grid, game loop và room concurrency.
- Input security và parsing/serialization protocol.
- WebSocket handler, registry, codec, origin properties và integration.

Frontend có 43 unit test:

- Registry/id/assets.
- Protocol builders/parser.
- `GameConnection` lifecycle/message handling.
- Zustand snapshot store.

Playwright E2E bao phủ multiplayer, evolution, death/respawn. Scripts:

- `load-test.mjs` và `load-test-scenarios.sh`: fake WebSocket clients.
- `probe-death.mjs`: kiểm tra death message.
- `verify-clean-clone.sh`: build/test trong clean copy.
- `check-id-consistency.sh`: đồng bộ ID frontend/backend.

## 10. Build, cấu hình và deploy

Các biến chính:

- `VITE_WS_URL`: URL WebSocket client.
- `GAME_WEBSOCKET_ALLOWED_ORIGIN_PATTERNS`: origin allowlist.
- Các property `game.world.*`, `game.room.max-players`,
  `game.loop.tick-rate` trong `application.yml`.

Development Compose chạy Vite + Spring Boot. Production Compose build frontend
static và dùng nginx proxy `/ws`; backend Dockerfile build Maven multi-stage.
Lockfile được commit và workflow dùng `npm ci`.

## 11. Sơ đồ

Các file draw.io có thể mở trực tiếp tại
[app.diagrams.net](https://app.diagrams.net/):

- [`diagrams/class-diagram.drawio`](diagrams/class-diagram.drawio): lớp/module và quan hệ.
- [`diagrams/usecase-diagram.drawio`](diagrams/usecase-diagram.drawio): actor/use case.
- [`diagrams/application-flowchart.drawio`](diagrams/application-flowchart.drawio): luồng end-to-end.
- [`diagrams/game-tick-activity.drawio`](diagrams/game-tick-activity.drawio): activity của một tick.

Các bản export nền trắng để chèn vào báo cáo:

- `application-flowchart.{png,svg}`
- `usecase-diagram.{png,svg}`
- `game-tick-activity.{png,svg}`
- `class-diagram-overview.{png,svg}`
- `class-diagram-backend-core.{png,svg}`

## 12. Hạn chế và hướng mở rộng

- Chỉ một room/in-memory; restart mất toàn bộ trạng thái.
- Client/server registry khai báo kép, cần tiếp tục chạy consistency check.
- Global `window.__mimope_connection` là cầu nối thực dụng nhưng có thể thay
  bằng context/service owner rõ lifecycle hơn.
- `PixiGame` là lớp lớn; có thể tách terrain, entity renderer, camera và effects.
- Snapshot là JSON text; quy mô lớn có thể cần binary/delta compression.
- Spatial grid được rebuild hai lần/tick để ưu tiên correctness; cần benchmark
  trước khi tối ưu incremental.
- Chưa có authentication, matchmaking, persistence, moderation hay telemetry
  production-grade.
