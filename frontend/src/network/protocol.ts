/**
 * Shared protocol types for Mimope client ↔ server WebSocket communication.
 *
 * All message types, constants, and parsing utilities are defined here
 * so the rest of the frontend can import typed protocol objects rather
 * than working with raw JSON.
 */

// ---------------------------------------------------------------------------
// Protocol version — must match backend ProtocolConstants.PROTOCOL_VERSION
// ---------------------------------------------------------------------------

export const PROTOCOL_VERSION = 1;

// ---------------------------------------------------------------------------
// Message type constants
// ---------------------------------------------------------------------------

/** Client → Server message types. */
export const ClientMessageType = {
  JOIN: 'join',
  INPUT: 'input',
  EVOLVE: 'evolve',
  PING: 'ping',
  GRID_DEBUG: 'grid_debug',
} as const;

/** Server → Client message types. */
export const ServerMessageType = {
  WELCOME: 'welcome',
  SNAPSHOT: 'snapshot',
  EVOLUTION_OPTIONS: 'evolution_options',
  DEATH: 'death',
  PONG: 'pong',
  ERROR: 'error',
} as const;

export type ClientMessageTypeValue =
  (typeof ClientMessageType)[keyof typeof ClientMessageType];
export type ServerMessageTypeValue =
  (typeof ServerMessageType)[keyof typeof ServerMessageType];

// ---------------------------------------------------------------------------
// Client → Server messages
// ---------------------------------------------------------------------------

/** Sent when the player wants to join the game. */
export interface JoinMessage {
  type: typeof ClientMessageType.JOIN;
  nickname: string;
  starterAnimalId?: string;
}

/** Sent at a throttled rate with the player's current input state. */
export interface InputMessage {
  type: typeof ClientMessageType.INPUT;
  seq: number;
  angle: number;
  intensity: number;
  boost: boolean;
  ability: boolean;
  timestamp: number;
}

/** Sent when the player selects an evolution option. */
export interface EvolveMessage {
  type: typeof ClientMessageType.EVOLVE;
  animalId: string;
}

/** Sent periodically for latency measurement. */
export interface PingMessage {
  type: typeof ClientMessageType.PING;
  timestamp: number;
}

export interface GridDebugMessage {
  type: typeof ClientMessageType.GRID_DEBUG;
  enabled: boolean;
}

/** Union of all client → server messages. */
export type ClientMessage =
  | JoinMessage
  | InputMessage
  | EvolveMessage
  | PingMessage
  | GridDebugMessage;

// ---------------------------------------------------------------------------
// Server → Client messages
// ---------------------------------------------------------------------------

/** Sent after a successful join. */
export interface WelcomeMessage {
  type: typeof ServerMessageType.WELCOME;
  playerId: string;
  nickname: string;
  protocolVersion: number;
}

/** Player data within a snapshot. */
export interface SnapshotPlayerData {
  id: string;
  nickname: string;
  x: number;
  y: number;
  radius: number;
  angle: number;
  animalId: string;
  skinId?: string;
  health: number;
  maxHealth: number;
  xp: number;
  abilityCooldownTicks: number;
}

/** Food data within a snapshot. */
export interface SnapshotFoodData {
  id: string;
  foodId: string;
  x: number;
  y: number;
}

/** Leaderboard entry within a snapshot. */
export interface LeaderboardEntry {
  nickname: string;
  xp: number;
}

/** Food pickup event within a snapshot (for visual feedback). */
export interface FoodPickupData {
  foodInstanceId: string;
  foodId: string;
  x: number;
  y: number;
  xp: number;
  playerId: string;
}

/** Kill event within a snapshot (for visual feedback). */
export interface KillEventData {
  victimId: string;
  killerId: string;
  killerNickname: string;
  x: number;
  y: number;
  xpAwarded: number;
}

/** Ability event within a snapshot (for visual effects). */
export interface AbilityEventData {
  playerId: string;
  abilityId: string;
  x: number;
  y: number;
  angle: number;
}

/** Grid cell info for debug visualization. */
export interface GridCellDebug {
  x: number;
  y: number;
  w: number;
  h: number;
  playerCount: number;
  foodCount: number;
}

/** Periodic world state update. */
export interface SnapshotMessage {
  type: typeof ServerMessageType.SNAPSHOT;
  tick: number;
  players: SnapshotPlayerData[];
  foods: SnapshotFoodData[];
  leaderboard: LeaderboardEntry[];
  foodPickups?: FoodPickupData[];
  killEvents?: KillEventData[];
  abilityEvents?: AbilityEventData[];
  gridDebug?: GridCellDebug[];
}

/** Evolution option presented to the player. */
export interface EvolutionOption {
  animalId: string;
  name: string;
  tier: number;
}

/** Sent when the player reaches an XP threshold and can evolve. */
export interface EvolutionOptionsMessage {
  type: typeof ServerMessageType.EVOLUTION_OPTIONS;
  options: EvolutionOption[];
}

/** Sent when the player dies. */
export interface DeathMessage {
  type: typeof ServerMessageType.DEATH;
  reason: string;
  killerNickname?: string;
  xpEarned: number;
  survivalTimeMs: number;
}

/** Response to a client ping. */
export interface PongMessage {
  type: typeof ServerMessageType.PONG;
  timestamp?: number;
}

/** Generic error from the server. */
export interface ErrorServerMessage {
  type: typeof ServerMessageType.ERROR;
  message: string;
}

/** Union of all server → client messages. */
export type ServerMessage =
  | WelcomeMessage
  | SnapshotMessage
  | EvolutionOptionsMessage
  | DeathMessage
  | PongMessage
  | ErrorServerMessage;

// ---------------------------------------------------------------------------
// Message builders (client → server)
// ---------------------------------------------------------------------------

export function createJoinMessage(nickname: string, starterAnimalId?: string): JoinMessage {
  return { type: ClientMessageType.JOIN, nickname, starterAnimalId };
}

export function createInputMessage(
  seq: number,
  angle: number,
  intensity: number,
  boost: boolean,
  ability: boolean,
): InputMessage {
  return {
    type: ClientMessageType.INPUT,
    seq,
    angle,
    intensity,
    boost,
    ability,
    timestamp: Date.now(),
  };
}

export function createEvolveMessage(animalId: string): EvolveMessage {
  return { type: ClientMessageType.EVOLVE, animalId };
}

export function createPingMessage(): PingMessage {
  return { type: ClientMessageType.PING, timestamp: Date.now() };
}

export function createGridDebugMessage(enabled: boolean): GridDebugMessage {
  return { type: ClientMessageType.GRID_DEBUG, enabled };
}

// ---------------------------------------------------------------------------
// Message parsing (server → client)
// ---------------------------------------------------------------------------

/**
 * Parse a raw JSON string from the server into a typed ServerMessage.
 *
 * @returns the parsed message, or `null` if the JSON is invalid or
 *          has an unknown/missing type field.
 */
export function parseServerMessage(json: string): ServerMessage | null {
  try {
    const data = JSON.parse(json);
    if (typeof data !== 'object' || data === null || typeof data.type !== 'string') {
      return null;
    }

    switch (data.type) {
      case ServerMessageType.WELCOME:
        return parseWelcome(data);
      case ServerMessageType.SNAPSHOT:
        return parseSnapshot(data);
      case ServerMessageType.EVOLUTION_OPTIONS:
        return parseEvolutionOptions(data);
      case ServerMessageType.DEATH:
        return parseDeath(data);
      case ServerMessageType.PONG:
        return parsePong(data);
      case ServerMessageType.ERROR:
        return parseError(data);
      default:
        return null;
    }
  } catch {
    return null;
  }
}

// ---------------------------------------------------------------------------
// Individual parsers with validation
// ---------------------------------------------------------------------------

function parseWelcome(data: Record<string, unknown>): WelcomeMessage | null {
  if (typeof data.playerId !== 'string' || typeof data.nickname !== 'string') {
    return null;
  }
  return {
    type: ServerMessageType.WELCOME,
    playerId: data.playerId,
    nickname: data.nickname,
    protocolVersion: typeof data.protocolVersion === 'number' ? data.protocolVersion : 0,
  };
}

function parseSnapshot(data: Record<string, unknown>): SnapshotMessage | null {
  if (typeof data.tick !== 'number' || !Array.isArray(data.players) || !Array.isArray(data.foods)) {
    return null;
  }
  return {
    type: ServerMessageType.SNAPSHOT,
    tick: data.tick,
    players: data.players as SnapshotPlayerData[],
    foods: data.foods as SnapshotFoodData[],
    leaderboard: Array.isArray(data.leaderboard)
      ? (data.leaderboard as LeaderboardEntry[])
      : [],
    foodPickups: Array.isArray(data.foodPickups)
      ? (data.foodPickups as FoodPickupData[])
      : undefined,
    killEvents: Array.isArray(data.killEvents)
      ? (data.killEvents as KillEventData[])
      : undefined,
    abilityEvents: Array.isArray(data.abilityEvents)
      ? (data.abilityEvents as AbilityEventData[])
      : undefined,
    gridDebug: Array.isArray(data.gridDebug)
      ? (data.gridDebug as GridCellDebug[])
      : undefined,
  };
}

function parseEvolutionOptions(data: Record<string, unknown>): EvolutionOptionsMessage | null {
  if (!Array.isArray(data.options)) {
    return null;
  }
  return {
    type: ServerMessageType.EVOLUTION_OPTIONS,
    options: data.options as EvolutionOption[],
  };
}

function parseDeath(data: Record<string, unknown>): DeathMessage | null {
  if (typeof data.reason !== 'string') {
    return null;
  }
  return {
    type: ServerMessageType.DEATH,
    reason: data.reason,
    killerNickname: typeof data.killerNickname === 'string' ? data.killerNickname : undefined,
    xpEarned: typeof data.xpEarned === 'number' ? data.xpEarned : 0,
    survivalTimeMs: typeof data.survivalTimeMs === 'number' ? data.survivalTimeMs : 0,
  };
}

function parsePong(data: Record<string, unknown>): PongMessage | null {
  return {
    type: ServerMessageType.PONG,
    timestamp: typeof data.timestamp === 'number' ? data.timestamp : undefined,
  };
}

function parseError(data: Record<string, unknown>): ErrorServerMessage | null {
  if (typeof data.message !== 'string') {
    return null;
  }
  return {
    type: ServerMessageType.ERROR,
    message: data.message,
  };
}
