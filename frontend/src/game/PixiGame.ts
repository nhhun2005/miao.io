/**
 * PixiGame — Core PixiJS lifecycle manager for Mimope.
 *
 * Phase 9: Renders from server snapshots with interpolation.
 * No more local test movement — all player state comes from the server.
 *
 * Uses PixiJS v8 API (Application.init is async, Assets API, Container).
 */

import {
  Application,
  Container,
  Assets,
  Sprite,
  Graphics,
  Text,
  TextStyle,
  Texture,
  Ticker,
} from 'pixi.js';

import { ANIMALS } from './data/animals';
import { FOODS } from './data/foods';
import {
  buildAssetManifest,
  animalSkinKey,
  foodImageKey,
} from './data/assets';
import { InputManager } from './InputManager';
import { useInputStore } from '../state/inputStore';
import { useGameStore, type PlayerSnapshot, type FoodSnapshot } from '../state/gameStore';
import type { GameConnection } from '../network/GameConnection';
import type { SnapshotMessage } from '../network/protocol';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** World size in pixels (must match backend). */
const WORLD_WIDTH = 5000;
const WORLD_HEIGHT = 5000;

/** Grid cell size for background pattern. */
const BG_GRID_SIZE = 100;

/** Base zoom level (camera scale). */
const BASE_ZOOM = 1.0;

/** Min / max zoom limits. */
const MIN_ZOOM = 0.3;
const MAX_ZOOM = 2.0;

/** Interpolation speed (0–1 per frame, higher = snappier). */
const INTERP_SPEED = 0.15;

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface PixiGameOptions {
  /** The DOM element to attach the canvas to. */
  container: HTMLElement;
  /** The GameConnection to send inputs and receive snapshots. */
  connection: GameConnection;
  /** Whether to show the spatial grid debug overlay. */
  showGridDebug?: boolean;
}

/** State tracked per rendered player sprite. */
interface PlayerRenderState {
  container: Container;
  sprite: Sprite;
  nameLabel: Text;
  healthBar: Graphics;
  // Current displayed position (interpolated)
  displayX: number;
  displayY: number;
  displayAngle: number;
  // Target position from latest snapshot
  targetX: number;
  targetY: number;
  targetAngle: number;
  // Data
  animalId: string;
  radius: number;
  health: number;
  maxHealth: number;
  nickname: string;
}

/** State tracked per rendered food sprite. */
interface FoodRenderState {
  sprite: Sprite;
  foodId: string;
}

/** A floating "+XP" text effect that fades out. */
interface FoodPickupEffect {
  text: Text;
  /** Remaining lifetime in seconds. */
  ttl: number;
  /** Start Y in world coordinates. */
  startY: number;
  /** World X position. */
  worldX: number;
}

/** Duration of the pickup effect in seconds. */
const PICKUP_EFFECT_DURATION = 0.8;

/** How many pixels the text floats upward over its lifetime. */
const PICKUP_EFFECT_FLOAT_DISTANCE = 40;

// ---------------------------------------------------------------------------
// PixiGame class
// ---------------------------------------------------------------------------

export class PixiGame {
  private app!: Application;
  private container: HTMLElement;
  private connection: GameConnection;
  private destroyed = false;

  // Rendering layers (added to stage in order)
  private layerBackground!: Container;
  private layerTerrain!: Container;
  private layerFood!: Container;
  private layerPlayers!: Container;
  private layerEffects!: Container;
  private layerDebug!: Container;

  // Camera state
  private cameraX = WORLD_WIDTH / 2;
  private cameraY = WORLD_HEIGHT / 2;
  private zoom = BASE_ZOOM;
  private targetZoom = BASE_ZOOM;

  // World container (everything except debug HUD)
  private worldContainer!: Container;

  // Rendered entities
  private playerSprites: Map<string, PlayerRenderState> = new Map();
  private foodSprites: Map<string, FoodRenderState> = new Map();

  // Food pickup visual effects
  private pickupEffects: FoodPickupEffect[] = [];

  // Grid debug graphics
  private gridDebugGraphics: Graphics | null = null;
  private gridDebugLabels: Text[] = [];

  // FPS tracking
  private fpsText: Text | null = null;
  private fpsFrames = 0;
  private fpsElapsed = 0;
  private fpsValue = 0;

  // Asset loading state
  private assetsLoaded = false;

  // Last server snapshot tick that has been applied to Pixi display objects.
  // GameConnection always stores snapshots in gameStore before invoking this
  // renderer callback; tracking this lets the render loop recover if a
  // snapshot arrives before assets finish loading or while the callback is
  // temporarily detached during React remounts.
  private lastRenderedSnapshotTick = 0;

  // Input manager
  private inputManager: InputManager | null = null;

  // Snapshot callback binding
  private boundOnSnapshot: (msg: SnapshotMessage) => void;

  constructor(options: PixiGameOptions) {
    this.container = options.container;
    this.connection = options.connection;
    this.boundOnSnapshot = this._onSnapshot.bind(this);

    // If showGridDebug is enabled, request grid debug data from the server
    if (options.showGridDebug) {
      this.connection.sendGridDebugToggle(true);
    }
  }

  // -----------------------------------------------------------------------
  // Public API
  // -----------------------------------------------------------------------

  /** Initialize the PixiJS app, load assets, and start rendering. */
  async init(): Promise<void> {
    if (this.destroyed) return;

    this.app = new Application();

    await this.app.init({
      background: '#1a5c2a',
      resizeTo: this.container,
      antialias: true,
      autoDensity: true,
      resolution: window.devicePixelRatio || 1,
    });

    if (this.destroyed) {
      this.app.destroy(true);
      return;
    }

    // Mount canvas into the DOM
    this.container.appendChild(this.app.canvas as HTMLCanvasElement);

    // Create rendering layers
    this.createLayers();

    // Load assets
    await this.loadAssets();

    if (this.destroyed) {
      this.app.destroy(true);
      return;
    }

    this.assetsLoaded = true;

    // Build scene
    this.renderBackground();
    this.createFpsOverlay();

    // Set up input
    this.setupInput();

    // Start render loop
    this.app.ticker.add(this.onTick);
  }

  /** Destroy the PixiJS app and clean up all resources. */
  destroy(): void {
    this.destroyed = true;

    // Detach input manager
    if (this.inputManager) {
      this.inputManager.detach();
      this.inputManager = null;
    }

    // Reset input store
    useInputStore.getState().reset();

    if (this.app) {
      this.app.ticker.remove(this.onTick);

      // Remove canvas from DOM
      const canvas = this.app.canvas as HTMLCanvasElement;
      if (canvas.parentElement) {
        canvas.parentElement.removeChild(canvas);
      }

      this.app.destroy(true, { children: true, texture: true });
    }

    // Remove wheel listener
    this.container.removeEventListener('wheel', this.onWheel);

    // Clear sprite maps
    this.playerSprites.clear();
    this.foodSprites.clear();
  }

  /** Get the snapshot callback to wire into GameConnection. */
  get onSnapshot(): (msg: SnapshotMessage) => void {
    return this.boundOnSnapshot;
  }

  // -----------------------------------------------------------------------
  // Layer setup
  // -----------------------------------------------------------------------

  private createLayers(): void {
    this.worldContainer = new Container();
    this.worldContainer.label = 'world';
    this.app.stage.addChild(this.worldContainer);

    this.layerBackground = new Container();
    this.layerBackground.label = 'background';
    this.worldContainer.addChild(this.layerBackground);

    this.layerTerrain = new Container();
    this.layerTerrain.label = 'terrain';
    this.worldContainer.addChild(this.layerTerrain);

    this.layerFood = new Container();
    this.layerFood.label = 'food';
    this.worldContainer.addChild(this.layerFood);

    this.layerPlayers = new Container();
    this.layerPlayers.label = 'players';
    this.worldContainer.addChild(this.layerPlayers);

    this.layerEffects = new Container();
    this.layerEffects.label = 'effects';
    this.worldContainer.addChild(this.layerEffects);

    // Debug layer is on top of stage (not affected by camera)
    this.layerDebug = new Container();
    this.layerDebug.label = 'debug';
    this.app.stage.addChild(this.layerDebug);
  }

  // -----------------------------------------------------------------------
  // Asset loading
  // -----------------------------------------------------------------------

  private async loadAssets(): Promise<void> {
    const manifest = buildAssetManifest('/');

    // Register all assets with the PixiJS Assets system
    for (const entry of manifest) {
      Assets.add({ alias: entry.key, src: entry.path });
    }

    // Load all animal skins and food images
    const keysToLoad: string[] = [];

    for (const animalId of Object.keys(ANIMALS)) {
      keysToLoad.push(animalSkinKey(animalId));
    }

    for (const foodId of Object.keys(FOODS)) {
      keysToLoad.push(foodImageKey(foodId));
    }

    try {
      await Assets.load(keysToLoad);
    } catch (err) {
      console.warn('[PixiGame] Some assets failed to load:', err);
    }
  }

  // -----------------------------------------------------------------------
  // Background rendering
  // -----------------------------------------------------------------------

  private renderBackground(): void {
    const bg = new Graphics();

    // Solid green background
    bg.rect(0, 0, WORLD_WIDTH, WORLD_HEIGHT);
    bg.fill(0x1a5c2a);

    // Grid lines
    bg.setStrokeStyle({ width: 1, color: 0x1e6b30, alpha: 0.5 });

    for (let x = 0; x <= WORLD_WIDTH; x += BG_GRID_SIZE) {
      bg.moveTo(x, 0);
      bg.lineTo(x, WORLD_HEIGHT);
    }
    for (let y = 0; y <= WORLD_HEIGHT; y += BG_GRID_SIZE) {
      bg.moveTo(0, y);
      bg.lineTo(WORLD_WIDTH, y);
    }
    bg.stroke();

    // World border
    bg.setStrokeStyle({ width: 4, color: 0xff4444, alpha: 0.8 });
    bg.rect(0, 0, WORLD_WIDTH, WORLD_HEIGHT);
    bg.stroke();

    this.layerBackground.addChild(bg);
  }

  // -----------------------------------------------------------------------
  // FPS overlay
  // -----------------------------------------------------------------------

  private createFpsOverlay(): void {
    const style = new TextStyle({
      fontSize: 14,
      fontFamily: 'monospace',
      fill: 0x00ff00,
      stroke: { color: 0x000000, width: 2 },
    });
    this.fpsText = new Text({ text: 'FPS: --', style });
    this.fpsText.position.set(10, 10);
    this.layerDebug.addChild(this.fpsText);
  }

  // -----------------------------------------------------------------------
  // Input handling
  // -----------------------------------------------------------------------

  private setupInput(): void {
    this.inputManager = new InputManager({
      container: this.container,
      sendRate: 20,
      onInput: (snapshot) => {
        // Sync to Zustand store for React HUD components
        const offset = this.inputManager!.pointerOffset;
        useInputStore.getState().syncFromInput({
          angle: snapshot.angle,
          intensity: snapshot.intensity,
          boost: snapshot.boost,
          ability: snapshot.ability,
          seq: snapshot.seq,
          focused: this.inputManager!.focused,
          pointerX: offset.x,
          pointerY: offset.y,
        });

        // Send input to server via WebSocket
        this.connection.sendInput(
          snapshot.seq,
          snapshot.angle,
          snapshot.intensity,
          snapshot.boost,
          snapshot.ability,
        );
      },
    });
    this.inputManager.attach();

    // Zoom is still handled directly (not part of the input protocol)
    this.container.addEventListener('wheel', this.onWheel, { passive: true });
  }

  private onWheel = (e: WheelEvent): void => {
    const zoomDelta = -e.deltaY * 0.001;
    this.targetZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, this.targetZoom + zoomDelta));
  };

  // -----------------------------------------------------------------------
  // Snapshot handling — called by GameConnection
  // -----------------------------------------------------------------------

  private _onSnapshot(msg: SnapshotMessage): void {
    if (this.destroyed || !this.assetsLoaded) return;

    this.applySnapshotToScene(msg.players, msg.foods, msg.tick);

    // Spawn visual effects for food pickups
    if (msg.foodPickups && msg.foodPickups.length > 0) {
      for (const pickup of msg.foodPickups) {
        this.spawnPickupEffect(pickup.x, pickup.y, pickup.xp);
      }
    }
  }

  /**
   * Synchronize Pixi display objects from the latest Zustand game store state.
   *
   * This is intentionally called from the ticker in addition to the direct
   * WebSocket callback path. It removes a timing dependency between network
   * snapshots and Pixi asset initialization, which otherwise can result in a
   * joined game with an empty scene until a future callback is delivered.
   */
  private syncLatestStoreSnapshot(): void {
    const state = useGameStore.getState();
    if (state.lastSnapshotTick <= this.lastRenderedSnapshotTick) return;

    this.applySnapshotToScene(
      Object.values(state.players),
      Object.values(state.foods),
      state.lastSnapshotTick,
    );
  }

  private applySnapshotToScene(
    players: SnapshotMessage['players'],
    foods: SnapshotMessage['foods'],
    tick: number,
  ): void {
    this.updatePlayerSprites(players);
    this.updateFoodSprites(foods);
    this.lastRenderedSnapshotTick = tick;
  }

  // -----------------------------------------------------------------------
  // Player sprite management
  // -----------------------------------------------------------------------

  private updatePlayerSprites(players: SnapshotMessage['players']): void {
    const seenIds = new Set<string>();

    for (const p of players) {
      seenIds.add(p.id);

      let state = this.playerSprites.get(p.id);
      if (!state) {
        // Create new player sprite
        state = this.createPlayerSprite(p);
        this.playerSprites.set(p.id, state);
      }

      // Update target position for interpolation
      state.targetX = p.x;
      state.targetY = p.y;
      state.targetAngle = p.angle;
      state.health = p.health;
      state.maxHealth = p.maxHealth;

      // If the animal changed, update the sprite texture
      if (state.animalId !== p.animalId) {
        this.updatePlayerAnimalSprite(state, p.animalId, p.radius);
      }

      // Update radius if changed
      if (state.radius !== p.radius) {
        state.radius = p.radius;
        state.sprite.width = p.radius * 2;
        state.sprite.height = p.radius * 2;
      }

      // Update nickname if changed
      if (state.nickname !== p.nickname) {
        state.nickname = p.nickname;
        state.nameLabel.text = p.nickname;
      }
    }

    // Remove sprites for players no longer in snapshot
    for (const [id, state] of this.playerSprites) {
      if (!seenIds.has(id)) {
        this.layerPlayers.removeChild(state.container);
        state.container.destroy({ children: true });
        this.playerSprites.delete(id);
      }
    }
  }

  private createPlayerSprite(p: PlayerSnapshot): PlayerRenderState {
    const playerContainer = new Container();
    playerContainer.label = `player-${p.id}`;

    // Animal sprite
    const key = animalSkinKey(p.animalId);
    const texture = Assets.get<Texture>(key);

    let sprite: Sprite;
    if (texture) {
      sprite = new Sprite(texture);
    } else {
      // Fallback: colored circle
      const g = new Graphics();
      g.circle(0, 0, p.radius);
      g.fill(0x84cc16);
      const fallbackTexture = this.app.renderer.generateTexture(g);
      sprite = new Sprite(fallbackTexture);
      g.destroy();
    }

    sprite.anchor.set(0.5);
    sprite.width = p.radius * 2;
    sprite.height = p.radius * 2;
    playerContainer.addChild(sprite);

    // Nickname label
    const localPlayerId = useGameStore.getState().localPlayerId;
    const isLocalPlayer = p.id === localPlayerId;

    const labelStyle = new TextStyle({
      fontSize: 14,
      fontWeight: 'bold',
      fill: isLocalPlayer ? 0x00ff88 : 0xffffff,
      stroke: { color: 0x000000, width: 3 },
      align: 'center',
    });
    const nameLabel = new Text({ text: p.nickname, style: labelStyle });
    nameLabel.anchor.set(0.5);
    nameLabel.position.set(0, -p.radius - 14);
    playerContainer.addChild(nameLabel);

    // Health bar background
    const healthBar = new Graphics();
    playerContainer.addChild(healthBar);

    // Position at the snapshot position initially (no interp for first frame)
    playerContainer.position.set(p.x, p.y);

    this.layerPlayers.addChild(playerContainer);

    return {
      container: playerContainer,
      sprite,
      nameLabel,
      healthBar,
      displayX: p.x,
      displayY: p.y,
      displayAngle: p.angle,
      targetX: p.x,
      targetY: p.y,
      targetAngle: p.angle,
      animalId: p.animalId,
      radius: p.radius,
      health: p.health,
      maxHealth: p.maxHealth,
      nickname: p.nickname,
    };
  }

  private updatePlayerAnimalSprite(state: PlayerRenderState, newAnimalId: string, newRadius: number): void {
    // Remove old sprite
    state.container.removeChild(state.sprite);
    state.sprite.destroy();

    // Create new sprite
    const key = animalSkinKey(newAnimalId);
    const texture = Assets.get<Texture>(key);

    let newSprite: Sprite;
    if (texture) {
      newSprite = new Sprite(texture);
    } else {
      const g = new Graphics();
      g.circle(0, 0, newRadius);
      g.fill(0x84cc16);
      const fallbackTexture = this.app.renderer.generateTexture(g);
      newSprite = new Sprite(fallbackTexture);
      g.destroy();
    }

    newSprite.anchor.set(0.5);
    newSprite.width = newRadius * 2;
    newSprite.height = newRadius * 2;

    // Insert sprite at bottom of container (below label and health bar)
    state.container.addChildAt(newSprite, 0);

    state.sprite = newSprite;
    state.animalId = newAnimalId;
    state.radius = newRadius;

    // Update name label position
    state.nameLabel.position.set(0, -newRadius - 14);
  }

  // -----------------------------------------------------------------------
  // Food sprite management
  // -----------------------------------------------------------------------

  private updateFoodSprites(foods: SnapshotMessage['foods']): void {
    const seenIds = new Set<string>();

    for (const f of foods) {
      seenIds.add(f.id);

      if (!this.foodSprites.has(f.id)) {
        // Create new food sprite
        const state = this.createFoodSprite(f);
        this.foodSprites.set(f.id, state);
      }
    }

    // Remove sprites for food no longer in snapshot
    for (const [id, state] of this.foodSprites) {
      if (!seenIds.has(id)) {
        this.layerFood.removeChild(state.sprite);
        state.sprite.destroy();
        this.foodSprites.delete(id);
      }
    }
  }

  private createFoodSprite(f: FoodSnapshot): FoodRenderState {
    const foodDef = FOODS[f.foodId];
    const radius = foodDef?.radius ?? 8;
    const key = foodImageKey(f.foodId);
    const texture = Assets.get<Texture>(key);

    let sprite: Sprite;
    if (texture) {
      sprite = new Sprite(texture);
    } else {
      const g = new Graphics();
      g.circle(0, 0, radius);
      g.fill(0xef4444);
      const fallbackTexture = this.app.renderer.generateTexture(g);
      sprite = new Sprite(fallbackTexture);
      g.destroy();
    }

    sprite.anchor.set(0.5);
    sprite.width = radius * 2;
    sprite.height = radius * 2;
    sprite.position.set(f.x, f.y);

    this.layerFood.addChild(sprite);

    return { sprite, foodId: f.foodId };
  }

  // -----------------------------------------------------------------------
  // Food pickup visual effects
  // -----------------------------------------------------------------------

  /**
   * Spawn a floating "+XP" text at the given world position.
   * The text floats upward and fades out over PICKUP_EFFECT_DURATION seconds.
   */
  private spawnPickupEffect(worldX: number, worldY: number, xp: number): void {
    const style = new TextStyle({
      fontSize: 16,
      fontWeight: 'bold',
      fill: 0xffdd00,
      stroke: { color: 0x000000, width: 3 },
    });
    const text = new Text({ text: `+${xp}`, style });
    text.anchor.set(0.5);
    text.position.set(worldX, worldY);

    this.layerEffects.addChild(text);

    this.pickupEffects.push({
      text,
      ttl: PICKUP_EFFECT_DURATION,
      startY: worldY,
      worldX,
    });
  }

  /**
   * Animate pickup effects: float upward and fade out, then remove.
   */
  private updatePickupEffects(dt: number): void {
    for (let i = this.pickupEffects.length - 1; i >= 0; i--) {
      const effect = this.pickupEffects[i];
      effect.ttl -= dt;

      if (effect.ttl <= 0) {
        // Remove expired effect
        this.layerEffects.removeChild(effect.text);
        effect.text.destroy();
        this.pickupEffects.splice(i, 1);
        continue;
      }

      // Progress: 0 at start → 1 at end
      const progress = 1 - effect.ttl / PICKUP_EFFECT_DURATION;

      // Float upward
      effect.text.position.set(
        effect.worldX,
        effect.startY - progress * PICKUP_EFFECT_FLOAT_DISTANCE,
      );

      // Fade out (alpha goes from 1 → 0)
      effect.text.alpha = 1 - progress;

      // Slight scale-up for juiciness
      const scale = 1 + progress * 0.3;
      effect.text.scale.set(scale);
    }
  }

  // -----------------------------------------------------------------------
  // Render loop
  // -----------------------------------------------------------------------

  private onTick = (ticker: Ticker): void => {
    if (this.destroyed || !this.assetsLoaded) return;

    const dt = ticker.deltaMS / 1000;

    this.syncLatestStoreSnapshot();
    this.interpolatePlayers(dt);
    this.updateHealthBars();
    this.updatePickupEffects(dt);
    this.updateCamera(dt);
    this.updateGridDebug();
    this.updateFps(ticker.deltaMS);
  };

  // -----------------------------------------------------------------------
  // Grid debug visualization
  // -----------------------------------------------------------------------

  private updateGridDebug(): void {
    const gridDebug = useGameStore.getState().gridDebug;
    if (!gridDebug || gridDebug.length === 0) {
      if (this.gridDebugGraphics) {
        this.layerDebug.removeChild(this.gridDebugGraphics);
        this.gridDebugGraphics.destroy({ children: true });
        this.gridDebugGraphics = null;
      }
      // Clear label pool
      for (const label of this.gridDebugLabels) {
        label.destroy();
      }
      this.gridDebugLabels = [];
      return;
    }

    if (!this.gridDebugGraphics) {
      this.gridDebugGraphics = new Graphics();
      this.layerDebug.addChild(this.gridDebugGraphics);
    }

    const g = this.gridDebugGraphics;
    g.clear();

    // Remove old labels from graphics
    for (const label of this.gridDebugLabels) {
      if (label.parent === g) {
        g.removeChild(label);
      }
    }

    // Draw grid cell outlines
    g.setStrokeStyle({ width: 1, color: 0x00ffff, alpha: 0.3 });

    for (const cell of gridDebug) {
      g.rect(cell.x, cell.y, cell.w, cell.h);
    }
    g.stroke();

    // Reuse or create labels
    const style = new TextStyle({
      fontSize: 10,
      fontFamily: 'monospace',
      fill: 0x00ffff,
    });

    for (let i = 0; i < gridDebug.length; i++) {
      const cell = gridDebug[i];
      let label: Text;
      if (i < this.gridDebugLabels.length) {
        label = this.gridDebugLabels[i];
        label.text = `${cell.playerCount}p ${cell.foodCount}f`;
        label.visible = true;
      } else {
        label = new Text({
          text: `${cell.playerCount}p ${cell.foodCount}f`,
          style,
        });
        this.gridDebugLabels.push(label);
      }
      label.position.set(cell.x + 2, cell.y + 2);
      g.addChild(label);
    }

    // Hide unused labels
    for (let i = gridDebug.length; i < this.gridDebugLabels.length; i++) {
      this.gridDebugLabels[i].visible = false;
    }
  }

  // -----------------------------------------------------------------------
  // Interpolation — smoothly move sprites toward server positions
  // -----------------------------------------------------------------------

  private interpolatePlayers(_dt: number): void {
    for (const [, state] of this.playerSprites) {
      // Lerp position
      state.displayX += (state.targetX - state.displayX) * INTERP_SPEED;
      state.displayY += (state.targetY - state.displayY) * INTERP_SPEED;

      // Lerp angle (handling wraparound)
      let angleDiff = state.targetAngle - state.displayAngle;
      // Normalize to [-PI, PI]
      while (angleDiff > Math.PI) angleDiff -= 2 * Math.PI;
      while (angleDiff < -Math.PI) angleDiff += 2 * Math.PI;
      state.displayAngle += angleDiff * INTERP_SPEED;

      // Apply to container
      state.container.position.set(state.displayX, state.displayY);
      state.sprite.rotation = state.displayAngle + Math.PI / 2;
    }
  }

  // -----------------------------------------------------------------------
  // Health bars
  // -----------------------------------------------------------------------

  private updateHealthBars(): void {
    for (const [, state] of this.playerSprites) {
      const bar = state.healthBar;
      bar.clear();

      const barWidth = state.radius * 2;
      const barHeight = 4;
      const barY = state.radius + 6;
      const healthPct = Math.max(0, Math.min(1, state.health / state.maxHealth));

      // Background
      bar.rect(-barWidth / 2, barY, barWidth, barHeight);
      bar.fill({ color: 0x333333, alpha: 0.6 });

      // Health fill
      if (healthPct > 0) {
        const fillColor = healthPct > 0.5 ? 0x22c55e : healthPct > 0.25 ? 0xeab308 : 0xef4444;
        bar.rect(-barWidth / 2, barY, barWidth * healthPct, barHeight);
        bar.fill(fillColor);
      }
    }
  }

  // -----------------------------------------------------------------------
  // Camera
  // -----------------------------------------------------------------------

  private updateCamera(_dt: number): void {
    // Follow the local player
    const localPlayerId = useGameStore.getState().localPlayerId;
    if (localPlayerId) {
      const localState = this.playerSprites.get(localPlayerId);
      if (localState) {
        this.cameraX = localState.displayX;
        this.cameraY = localState.displayY;

        // Adjust zoom based on player radius
        const baseRadius = ANIMALS['mouse']?.radius ?? 22;
        const radiusRatio = localState.radius / baseRadius;
        this.targetZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, BASE_ZOOM / Math.sqrt(radiusRatio)));
      }
    }

    // Smooth zoom
    this.zoom += (this.targetZoom - this.zoom) * 0.1;

    const screenW = this.app.screen.width;
    const screenH = this.app.screen.height;

    // Position the world container so the camera target is centered
    this.worldContainer.scale.set(this.zoom);
    this.worldContainer.position.set(
      screenW / 2 - this.cameraX * this.zoom,
      screenH / 2 - this.cameraY * this.zoom,
    );
  }

  private updateFps(deltaMS: number): void {
    this.fpsFrames++;
    this.fpsElapsed += deltaMS;

    if (this.fpsElapsed >= 1000) {
      this.fpsValue = Math.round((this.fpsFrames * 1000) / this.fpsElapsed);
      this.fpsFrames = 0;
      this.fpsElapsed = 0;

      if (this.fpsText) {
        this.fpsText.text = `FPS: ${this.fpsValue}`;
      }
    }
  }

  // -----------------------------------------------------------------------
  // Public getters (for React overlays)
  // -----------------------------------------------------------------------

  get fps(): number {
    return this.fpsValue;
  }

  get currentZoom(): number {
    return this.zoom;
  }

  get playerPosition(): { x: number; y: number } {
    return { x: this.cameraX, y: this.cameraY };
  }

  /** Access to the input manager for external queries. */
  get input(): InputManager | null {
    return this.inputManager;
  }
}
