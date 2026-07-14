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
import { OutlineFilter } from 'pixi-filters';


import { ANIMALS } from './data/animals';
import { FOODS } from './data/foods';
import {
  buildAssetManifest,
  buildGameplaySkinKeys,
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

/**
 * Outline thickness (in texture pixels) for the shape-following border drawn
 * around players. The OutlineFilter traces the sprite's opaque pixels, so the
 * ring hugs the exact silhouette without covering or tinting the artwork.
 */
const PLAYER_OUTLINE_THICKNESS = 3;

/** Outline thickness for the smaller food sprites. */
const FOOD_OUTLINE_THICKNESS = 2;

/**
 * Fixed pixel gap between the sprite edge and its outline. OutlineFilter can
 * offset the outline outward via {@code padding}/{@code offset}; we grow the
 * filter's padding so the ring sits a couple pixels clear of the artwork
 * instead of flush against it.
 */
const OUTLINE_PADDING = 6;

// Threat / edibility colours for the shape-following outlines.
const OUTLINE_COLOR_THREAT = 0xff2d2d; // higher-tier opponent (red)
const OUTLINE_COLOR_NEUTRAL = 0x000000; // same/lower tier or local player (black)
const OUTLINE_COLOR_EDIBLE = 0x15803d; // food this player can eat (deep green)
const OUTLINE_COLOR_INEDIBLE = 0x000000; // food this player cannot eat (black)



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
  oceanSurvivalBar: Graphics;
  /**
   * Shape-following threat outline drawn directly on the animal sprite via a
   * filter. It traces the creature's opaque pixels so the coloured ring hugs
   * the exact silhouette without tinting or covering the artwork.
   */
  outline: OutlineFilter;
  /** Green counter-attack hitbox marker drawn at the tail. */
  tailHitbox: Graphics;
  /** Current outline colour, cached to avoid redundant writes. */
  outlineColor: number | null;


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
  skinId: string;
  radius: number;
  health: number;
  maxHealth: number;
  oceanSurvival?: number;
  maxOceanSurvival?: number;
  nickname: string;
}

/** State tracked per rendered food sprite. */
interface FoodRenderState {
  sprite: Sprite;
  /**
   * Shape-following edibility outline drawn directly on the food sprite via a
   * filter. It traces the food's opaque pixels so the coloured ring hugs the
   * exact silhouette without tinting or covering the artwork.
   */
  outline: OutlineFilter;
  foodId: string;

  /** Minimum animal tier required to eat this food. */
  minTier: number;
  /** Base radius of the food sprite. */
  radius: number;
  /** Current outline colour, cached to avoid redundant writes. */
  outlineColor: number | null;
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

interface EvolutionEffect {
  ring: Graphics;
  ttl: number;
  worldX: number;
  worldY: number;
  radius: number;
}

interface TimedGraphicEffect {
  graphic: Graphics;
  ttl: number;
  maxTtl: number;
}

/** Duration of the pickup effect in seconds. */
const PICKUP_EFFECT_DURATION = 0.8;

/** How many pixels the text floats upward over its lifetime. */
const PICKUP_EFFECT_FLOAT_DISTANCE = 40;

const EVOLUTION_EFFECT_DURATION = 0.9;

// ---------------------------------------------------------------------------
// PixiGame class
// ---------------------------------------------------------------------------

export class PixiGame {
  private app!: Application;
  private container: HTMLElement;
  private connection: GameConnection;
  private destroyed = false;
  /** True only once app.init() has fully resolved. */
  private appReady = false;

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
  private foodSpritePool: Sprite[] = [];

  // Food pickup visual effects
  private pickupEffects: FoodPickupEffect[] = [];
  private evolutionEffects: EvolutionEffect[] = [];
  private timedGraphicEffects: TimedGraphicEffect[] = [];

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
      background: '#2ecc71',
      resizeTo: this.container,
      antialias: true,
      autoDensity: true,
      resolution: window.devicePixelRatio || 1,
    });

    // The Application is now fully initialized; destroy() is safe from here.
    this.appReady = true;

    if (this.destroyed) {
      this.safeDestroyApp();
      return;
    }

    // Mount canvas into the DOM
    this.container.appendChild(this.app.canvas as HTMLCanvasElement);

    // Create rendering layers
    this.createLayers();

    // Load assets
    await this.loadAssets();

    if (this.destroyed) {
      this.safeDestroyApp();
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
      this.safeDestroyApp();
    }

    // Remove wheel listener
    this.container.removeEventListener('wheel', this.onWheel);

    // Clear sprite maps
    this.playerSprites.clear();
    this.foodSprites.clear();
    this.foodSpritePool = [];
  }

  /**
   * Destroy the PixiJS Application defensively.
   *
   * PixiJS v8 throws (e.g. "this._cancelResize is not a function") when
   * destroy() runs on an Application whose init() has not fully wired up its
   * systems, or when destroy() is invoked twice. Because destroy() is called
   * from React's synchronous unmount commit (for example when a death event
   * flips the active screen), an unguarded throw here aborts the commit and
   * prevents the next screen from mounting. Guarding keeps unmount safe.
   */
  private safeDestroyApp(): void {
    if (!this.app) return;

    try {
      this.app.ticker?.remove(this.onTick);
    } catch {
      // Ticker may not exist yet on a partially-initialized app.
    }

    // Remove canvas from DOM if it was mounted.
    try {
      const canvas = this.app.canvas as HTMLCanvasElement | undefined;
      if (canvas?.parentElement) {
        canvas.parentElement.removeChild(canvas);
      }
    } catch {
      // Canvas may not have been created on a partially-initialized app.
    }

    if (this.appReady) {
      try {
        this.app.destroy(true, { children: true, texture: true });
      } catch (err) {
        console.warn('[PixiGame] Error while destroying PixiJS app:', err);
      }
    }

    this.appReady = false;
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

    keysToLoad.push(...buildGameplaySkinKeys());

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
  // Outline helpers
  // -----------------------------------------------------------------------

  /**
   * Build a shape-following outline filter. The OutlineFilter traces the
   * sprite's opaque pixels, so the coloured ring hugs the exact silhouette
   * without a tinted underlay that could distort the artwork's colours. Extra
   * padding keeps the ring a couple pixels clear of the sprite edge.
   */
  private createOutlineFilter(thickness: number, color: number): OutlineFilter {
    const filter = new OutlineFilter({
      thickness,
      color,
      quality: 0.3,
      alpha: 1,
    });
    // Give the filter room so a thicker/offset outline is not clipped.
    filter.padding = thickness + OUTLINE_PADDING;
    return filter;
  }

  // -----------------------------------------------------------------------
  // Background rendering
  // -----------------------------------------------------------------------

  private renderBackground(): void {
    const bg = new Graphics();

    // Solid green background — fresher, more vivid grass tone
    bg.rect(0, 0, WORLD_WIDTH, WORLD_HEIGHT);
    bg.fill(0x2ecc71);

    // Ocean biome on the west side
    bg.rect(0, 0, WORLD_WIDTH * 0.28, WORLD_HEIGHT);
    bg.fill({ color: 0x1d4ed8, alpha: 0.85 });

    // Arctic biome across the southern map
    bg.rect(WORLD_WIDTH * 0.28, WORLD_HEIGHT * 0.64, WORLD_WIDTH * 0.72, WORLD_HEIGHT * 0.36);
    bg.fill({ color: 0xdbeafe, alpha: 0.75 });

    // River visual crossing land
    bg.rect(WORLD_WIDTH * 0.28, WORLD_HEIGHT * 0.42, WORLD_WIDTH * 0.72, 150);
    bg.fill({ color: 0x38bdf8, alpha: 0.55 });

    // Healing-stone placeholder
    bg.circle(WORLD_WIDTH * 0.62, WORLD_HEIGHT * 0.52, 42);
    bg.fill({ color: 0xa7f3d0, alpha: 0.7 });

    // Drinking-water puddles on land and arctic terrain. Positions and radii
    // mirror GameWorld.buildPuddles so aquatic animals can refill their water
    // bar exactly where they see water.
    const puddles: Array<[number, number, number]> = [
      // Grassland ponds
      [WORLD_WIDTH * 0.45, WORLD_HEIGHT * 0.22, 190],
      [WORLD_WIDTH * 0.72, WORLD_HEIGHT * 0.14, 150],
      [WORLD_WIDTH * 0.86, WORLD_HEIGHT * 0.46, 210],
      [WORLD_WIDTH * 0.55, WORLD_HEIGHT * 0.38, 170],
      [WORLD_WIDTH * 0.38, WORLD_HEIGHT * 0.52, 160],
      // Arctic ponds (southern band)
      [WORLD_WIDTH * 0.48, WORLD_HEIGHT * 0.8, 180],
      [WORLD_WIDTH * 0.8, WORLD_HEIGHT * 0.88, 200],
      [WORLD_WIDTH * 0.64, WORLD_HEIGHT * 0.72, 170],
    ];

    for (const [px, py, radius] of puddles) {
      bg.circle(px, py, radius);
      bg.fill({ color: 0x38bdf8, alpha: 0.65 });
      bg.circle(px, py, radius);
      bg.stroke({ width: 4, color: 0x0ea5e9, alpha: 0.7 });
    }


    // Grid lines
    bg.setStrokeStyle({ width: 1, color: 0x27ae60, alpha: 0.5 });

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
    if (msg.killEvents && msg.killEvents.length > 0) {
      for (const kill of msg.killEvents) {
        this.spawnKillEffect(kill.x, kill.y, kill.xpAwarded);
      }
    }
    if (msg.abilityEvents && msg.abilityEvents.length > 0) {
      for (const ability of msg.abilityEvents) {
        if (this.isDashAbility(ability.abilityId)) {
          this.spawnDashEffect(ability.x, ability.y, ability.angle);
        } else {
          this.spawnAbilityPulseEffect(ability.x, ability.y, ability.abilityId);
        }
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
      state.oceanSurvival = p.oceanSurvival;
      state.maxOceanSurvival = p.maxOceanSurvival;

      const skinId = p.skinId ?? p.animalId;

      // If the animal or cosmetic skin changed, update the sprite texture
      if (state.animalId !== p.animalId || state.skinId !== skinId) {
        this.updatePlayerAnimalSprite(state, p.animalId, skinId, p.radius);
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
    const skinId = p.skinId ?? p.animalId;
    const key = animalSkinKey(skinId);
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

    // Shape-following threat outline applied directly to the sprite. Because
    // it lives on the sprite (not a separate object), it disappears with the
    // sprite on evolution and never leaves an orphaned ring behind. Colour is
    // updated each tick in updatePlayerAdornments based on the local player's
    // tier.
    const outline = this.createOutlineFilter(PLAYER_OUTLINE_THICKNESS, OUTLINE_COLOR_NEUTRAL);
    sprite.filters = [outline];

    // Tail counter-attack hitbox marker — drawn beneath the sprite so it
    // reads as a glowing patch behind the animal. Only shown for players
    // whose tier is higher than the local player (see updatePlayerAdornments).
    const tailHitbox = new Graphics();
    playerContainer.addChild(tailHitbox);

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

    const oceanSurvivalBar = new Graphics();
    playerContainer.addChild(oceanSurvivalBar);

    // Position at the snapshot position initially (no interp for first frame)
    playerContainer.position.set(p.x, p.y);

    this.layerPlayers.addChild(playerContainer);

    return {
      container: playerContainer,
      sprite,
      nameLabel,
      healthBar,
      oceanSurvivalBar,
      outline,
      tailHitbox,
      outlineColor: null,
      displayX: p.x,
      displayY: p.y,
      displayAngle: p.angle,
      targetX: p.x,
      targetY: p.y,
      targetAngle: p.angle,
      animalId: p.animalId,
      skinId,
      radius: p.radius,
      health: p.health,
      maxHealth: p.maxHealth,
      oceanSurvival: p.oceanSurvival,
      maxOceanSurvival: p.maxOceanSurvival,
      nickname: p.nickname,
    };
  }

  private updatePlayerAnimalSprite(
    state: PlayerRenderState,
    newAnimalId: string,
    newSkinId: string,
    newRadius: number,
  ): void {
    // Remove old sprite (its outline filter is attached to it, so it is torn
    // down together — no orphaned border remains after evolution).
    state.container.removeChild(state.sprite);
    state.sprite.destroy();

    // Create new sprite
    const key = animalSkinKey(newSkinId);
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

    // Reapply a fresh outline filter to the new sprite and reset the cached
    // colour so updatePlayerAdornments repaints it on the next tick.
    const outline = this.createOutlineFilter(PLAYER_OUTLINE_THICKNESS, OUTLINE_COLOR_NEUTRAL);
    newSprite.filters = [outline];
    state.outline = outline;
    state.outlineColor = null;

    // Insert sprite above the tail hitbox but below the label and health bar.
    state.container.addChildAt(newSprite, 1);

    state.sprite = newSprite;
    state.animalId = newAnimalId;
    state.skinId = newSkinId;
    state.radius = newRadius;

    // Update name label position
    state.nameLabel.position.set(0, -newRadius - 14);
    this.spawnEvolutionEffect(state.displayX, state.displayY, newRadius);
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
        // Drop the outline filter so the pooled sprite starts clean on reuse.
        state.sprite.filters = [];
        state.sprite.visible = false;
        this.foodSpritePool.push(state.sprite);
        this.foodSprites.delete(id);
      }
    }

    // Refresh edible/non-edible borders based on the local player's tier.
    this.updateFoodBorders();
  }

  /**
   * Resolve the tier of the local player's current animal, or `null` if the
   * local player is not yet present in the scene.
   */
  private getLocalPlayerTier(): number | null {
    const localPlayerId = useGameStore.getState().localPlayerId;
    if (!localPlayerId) return null;
    const local = this.playerSprites.get(localPlayerId);
    if (!local) return null;
    return ANIMALS[local.animalId]?.tier ?? null;
  }

  /**
   * Recolour each food item's shape-following outline: deep green when the
   * local player's tier is high enough to eat it, black when it cannot yet be
   * eaten.
   */
  private updateFoodBorders(): void {
    const localTier = this.getLocalPlayerTier();

    for (const [, state] of this.foodSprites) {
      // Until we know the local player's tier, treat everything as edible so
      // the outline doesn't flash black on first spawn.
      const edible = localTier === null ? true : localTier >= state.minTier;
      const desiredColor = edible ? OUTLINE_COLOR_EDIBLE : OUTLINE_COLOR_INEDIBLE;

      // Skip redraw if the colour hasn't changed for this item.
      if (state.outlineColor === desiredColor) continue;
      state.outlineColor = desiredColor;
      state.outline.color = desiredColor;
    }
  }



  private createFoodSprite(f: FoodSnapshot): FoodRenderState {
    const foodDef = FOODS[f.foodId];
    const radius = foodDef?.radius ?? 8;
    const key = foodImageKey(f.foodId);
    const texture = Assets.get<Texture>(key);

    let sprite = this.foodSpritePool.pop();
    if (texture) {
      if (sprite) {
        sprite.texture = texture;
      } else {
        sprite = new Sprite(texture);
      }
    } else {
      const g = new Graphics();
      g.circle(0, 0, radius);
      g.fill(0xef4444);
      const fallbackTexture = this.app.renderer.generateTexture(g);
      if (sprite) {
        sprite.texture = fallbackTexture;
      } else {
        sprite = new Sprite(fallbackTexture);
      }
      g.destroy();
    }

    sprite.visible = true;
    sprite.alpha = 1;
    sprite.anchor.set(0.5);
    sprite.width = radius * 2;
    sprite.height = radius * 2;
    sprite.position.set(f.x, f.y);

    // Shape-following edibility outline applied directly to the food sprite.
    // Colour is decided in updateFoodBorders based on the local player's tier.
    const outline = this.createOutlineFilter(FOOD_OUTLINE_THICKNESS, OUTLINE_COLOR_INEDIBLE);
    sprite.filters = [outline];

    this.layerFood.addChild(sprite);

    const minTier = foodDef?.minTier ?? 1;

    return { sprite, outline, foodId: f.foodId, minTier, radius, outlineColor: null };
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

  private spawnEvolutionEffect(worldX: number, worldY: number, radius: number): void {
    const ring = new Graphics();
    ring.position.set(worldX, worldY);
    this.layerEffects.addChild(ring);

    this.evolutionEffects.push({
      ring,
      ttl: EVOLUTION_EFFECT_DURATION,
      worldX,
      worldY,
      radius,
    });
  }

  private updateEvolutionEffects(dt: number): void {
    for (let i = this.evolutionEffects.length - 1; i >= 0; i--) {
      const effect = this.evolutionEffects[i];
      effect.ttl -= dt;

      if (effect.ttl <= 0) {
        this.layerEffects.removeChild(effect.ring);
        effect.ring.destroy();
        this.evolutionEffects.splice(i, 1);
        continue;
      }

      const progress = 1 - effect.ttl / EVOLUTION_EFFECT_DURATION;
      const radius = effect.radius + progress * 45;
      effect.ring.clear();
      effect.ring.circle(0, 0, radius);
      effect.ring.stroke({
        width: 5 * (1 - progress),
        color: 0xfacc15,
        alpha: 1 - progress,
      });
      effect.ring.position.set(effect.worldX, effect.worldY);
    }
  }

  private spawnKillEffect(worldX: number, worldY: number, xpAwarded: number): void {
    const style = new TextStyle({
      fontSize: 20,
      fontWeight: 'bold',
      fill: 0xef4444,
      stroke: { color: 0xffffff, width: 3 },
    });
    const text = new Text({ text: `KO +${Math.round(xpAwarded)}`, style });
    text.anchor.set(0.5);
    text.position.set(worldX, worldY - 20);
    this.layerEffects.addChild(text);

    this.pickupEffects.push({
      text,
      ttl: PICKUP_EFFECT_DURATION,
      startY: worldY - 20,
      worldX,
    });
  }

  private spawnDashEffect(worldX: number, worldY: number, angle: number): void {
    const graphic = new Graphics();
    const tailLength = 90;
    const endX = -Math.cos(angle) * tailLength;
    const endY = -Math.sin(angle) * tailLength;

    graphic.position.set(worldX, worldY);
    graphic.setStrokeStyle({ width: 12, color: 0xfacc15, alpha: 0.65 });
    graphic.moveTo(0, 0);
    graphic.lineTo(endX, endY);
    graphic.stroke();
    this.layerEffects.addChild(graphic);

    this.timedGraphicEffects.push({ graphic, ttl: 0.35, maxTtl: 0.35 });
  }

  private spawnAbilityPulseEffect(worldX: number, worldY: number, abilityId: string): void {
    const graphic = new Graphics();
    const color = this.abilityColor(abilityId);
    graphic.position.set(worldX, worldY);
    graphic.circle(0, 0, 70);
    graphic.stroke({ width: 8, color, alpha: 0.72 });
    this.layerEffects.addChild(graphic);

    this.timedGraphicEffects.push({ graphic, ttl: 0.45, maxTtl: 0.45 });
  }

  private isDashAbility(abilityId: string): boolean {
    return abilityId === 'dash' || abilityId.includes('dash') || abilityId === 'charge';
  }

  private abilityColor(abilityId: string): number {
    if (abilityId.includes('fire')) return 0xf97316;
    if (abilityId.includes('freeze') || abilityId.includes('snow')) return 0x93c5fd;
    if (abilityId.includes('ink') || abilityId.includes('whirlpool')) return 0x312e81;
    if (abilityId.includes('shock')) return 0xfacc15;
    if (abilityId.includes('guard')) return 0x22c55e;
    return 0xef4444;
  }

  private updateTimedGraphicEffects(dt: number): void {
    for (let i = this.timedGraphicEffects.length - 1; i >= 0; i--) {
      const effect = this.timedGraphicEffects[i];
      effect.ttl -= dt;

      if (effect.ttl <= 0) {
        this.layerEffects.removeChild(effect.graphic);
        effect.graphic.destroy();
        this.timedGraphicEffects.splice(i, 1);
        continue;
      }

      effect.graphic.alpha = effect.ttl / effect.maxTtl;
    }
  }

  // -----------------------------------------------------------------------
  // Render loop
  // -----------------------------------------------------------------------

  private onTick = (ticker: Ticker): void => {
    if (this.destroyed || !this.assetsLoaded) return;

    const dt = ticker.deltaMS / 1000;

    this.syncLatestStoreSnapshot();
    this.interpolatePlayers();
    this.updatePlayerAdornments();
    this.updateHealthBars();
    this.updatePickupEffects(dt);
    this.updateEvolutionEffects(dt);
    this.updateTimedGraphicEffects(dt);
    this.updateCamera();
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
        this.gridDebugGraphics.parent?.removeChild(this.gridDebugGraphics);
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
      this.worldContainer.addChild(this.gridDebugGraphics);
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

  private interpolatePlayers(): void {
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
      state.sprite.rotation = state.displayAngle - Math.PI / 2;
    }
  }

  // -----------------------------------------------------------------------
  // Player adornments — threat/edible border ring and tail counter-attack box
  // -----------------------------------------------------------------------

  /**
   * Colour the shape-following outline and draw the tail hitbox marker for
   * every player relative to the local player's tier:
   *
   * - Higher-tier opponents (a threat) get a red outline and a green tail
   *   patch marking the counter-attack hitbox you can bite to fight back.
   * - Same-or-lower-tier players (and the local player) get a plain black
   *   outline and no tail marker.
   */
  private updatePlayerAdornments(): void {
    const localPlayerId = useGameStore.getState().localPlayerId;
    const localTier = this.getLocalPlayerTier();

    for (const [id, state] of this.playerSprites) {
      const isLocalPlayer = id === localPlayerId;
      const tier = ANIMALS[state.animalId]?.tier ?? 1;
      // A higher-tier opponent is a threat worth marking. The local player is
      // never a threat to itself.
      const isHigherTier = !isLocalPlayer && localTier !== null && tier > localTier;

      // Recolour the shape-following outline: red for higher-tier threats,
      // black otherwise. Only write when it actually changes.
      const desiredColor = isHigherTier ? OUTLINE_COLOR_THREAT : OUTLINE_COLOR_NEUTRAL;
      if (state.outlineColor !== desiredColor) {
        state.outlineColor = desiredColor;
        state.outline.color = desiredColor;
      }

      // Green counter-attack tail hitbox — only for higher-tier threats. The
      // marker sits behind the animal (opposite the facing direction) and
      // tracks the tail as the body rotates.
      const tail = state.tailHitbox;
      tail.clear();
      if (isHigherTier) {
        const tailDistance = state.radius * 0.85;
        const tailRadius = Math.max(6, state.radius * 0.45);
        // displayAngle points in the facing direction; the tail is opposite.
        const tx = -Math.cos(state.displayAngle) * tailDistance;
        const ty = -Math.sin(state.displayAngle) * tailDistance;
        tail.circle(tx, ty, tailRadius);
        tail.fill({ color: 0x22ff66, alpha: 0.4 });
        tail.circle(tx, ty, tailRadius);
        tail.stroke({ width: 2, color: 0x16a34a, alpha: 0.9 });
      }
    }
  }

  // -----------------------------------------------------------------------
  // Health bars
  // -----------------------------------------------------------------------

  private updateHealthBars(): void {

    for (const [, state] of this.playerSprites) {
      const bar = state.healthBar;
      bar.clear();
      state.oceanSurvivalBar.clear();

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

      // Water bar — shown for every creature. Boosting drains it, water
      // sources and food refill it, and running dry causes dehydration damage.
      const maxWater = state.maxOceanSurvival ?? 0;
      if (maxWater > 0) {
        const waterPct = Math.max(
          0,
          Math.min(1, (state.oceanSurvival ?? 0) / maxWater),
        );
        const waterBarY = barY + barHeight + 3;
        const waterBar = state.oceanSurvivalBar;

        waterBar.rect(-barWidth / 2, waterBarY, barWidth, barHeight);
        waterBar.fill({ color: 0x111827, alpha: 0.55 });

        if (waterPct > 0) {
          // Fade toward red as the water bar empties to warn of dehydration.
          const waterColor = waterPct > 0.25 ? 0x38bdf8 : 0xef4444;
          waterBar.rect(-barWidth / 2, waterBarY, barWidth * waterPct, barHeight);
          waterBar.fill(waterColor);
        }
      }
    }
  }

  // -----------------------------------------------------------------------
  // Camera
  // -----------------------------------------------------------------------

  private updateCamera(): void {
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
