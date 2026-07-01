/**
 * GameConnection — WebSocket connection manager for Mimope.
 *
 * Handles:
 * - Connecting to the game server
 * - Sending join, input, ping, and evolve messages
 * - Receiving and dispatching server messages (welcome, snapshot, death, etc.)
 * - Auto-ping for latency measurement
 * - Reconnection-aware lifecycle
 *
 * The connection is owned by the LoadingScreen / GameScreen flow and
 * pushes state into Zustand stores (gameStore, uiStore).
 */

import { WS_URL } from '../config/env';
import {
  createJoinMessage,
  createInputMessage,
  createPingMessage,
  parseServerMessage,
  ServerMessageType,
  type ServerMessage,
  type SnapshotMessage,
  type WelcomeMessage,
  type DeathMessage,
  type PongMessage,
  type ErrorServerMessage,
} from './protocol';
import { useGameStore } from '../state/gameStore';
import { useUIStore } from '../state/uiStore';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export type ConnectionState = 'disconnected' | 'connecting' | 'connected' | 'joined';

export interface GameConnectionCallbacks {
  /** Called when the connection state changes. */
  onStateChange?: (state: ConnectionState) => void;
  /** Called on every snapshot (for PixiGame rendering). */
  onSnapshot?: (msg: SnapshotMessage) => void;
  /** Called on error messages from server. */
  onError?: (message: string) => void;
}

// ---------------------------------------------------------------------------
// GameConnection class
// ---------------------------------------------------------------------------

export class GameConnection {
  private ws: WebSocket | null = null;
  private state: ConnectionState = 'disconnected';
  private callbacks: GameConnectionCallbacks;
  private pingTimer: ReturnType<typeof setInterval> | null = null;
  private lastPingTime = 0;
  private _latency = 0;
  private destroyed = false;

  constructor(callbacks: GameConnectionCallbacks = {}) {
    this.callbacks = callbacks;
  }

  // -----------------------------------------------------------------------
  // Public API
  // -----------------------------------------------------------------------

  /** Connect to the game server WebSocket. */
  connect(): void {
    if (this.destroyed) return;
    if (this.ws) {
      this.disconnect();
    }

    this.setState('connecting');

    try {
      this.ws = new WebSocket(WS_URL);
    } catch (err) {
      console.error('[GameConnection] Failed to create WebSocket:', err);
      this.setState('disconnected');
      this.callbacks.onError?.('Failed to connect to server.');
      return;
    }

    this.ws.onopen = this.onOpen;
    this.ws.onmessage = this.onMessage;
    this.ws.onclose = this.onClose;
    this.ws.onerror = this.onWsError;
  }

  /** Disconnect from the server. */
  disconnect(): void {
    this.stopPing();
    if (this.ws) {
      this.ws.onopen = null;
      this.ws.onmessage = null;
      this.ws.onclose = null;
      this.ws.onerror = null;
      if (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING) {
        this.ws.close();
      }
      this.ws = null;
    }
    this.setState('disconnected');
  }

  /** Send join message with nickname. */
  join(nickname: string): void {
    this.send(createJoinMessage(nickname));
  }

  /** Send an input message. */
  sendInput(seq: number, angle: number, intensity: number, boost: boolean, ability: boolean): void {
    this.send(createInputMessage(seq, angle, intensity, boost, ability));
  }

  /** Send grid debug toggle message. */
  sendGridDebugToggle(enabled: boolean): void {
    this.send({ type: 'grid_debug', enabled });
  }

  /** Send a ping for latency measurement. */
  sendPing(): void {
    this.lastPingTime = Date.now();
    this.send(createPingMessage());
  }

  /** Destroy the connection permanently. */
  destroy(): void {
    this.destroyed = true;
    this.disconnect();
  }

  /** Set or replace the snapshot callback (used by PixiGame). */
  setSnapshotCallback(cb: ((msg: SnapshotMessage) => void) | undefined): void {
    this.callbacks.onSnapshot = cb;
  }

  /** Current connection state. */
  get connectionState(): ConnectionState {
    return this.state;
  }

  /** Latest measured round-trip latency in ms. */
  get latency(): number {
    return this._latency;
  }

  /** Whether the WebSocket is open and ready. */
  get isConnected(): boolean {
    return this.ws !== null && this.ws.readyState === WebSocket.OPEN;
  }

  // -----------------------------------------------------------------------
  // Internal — WebSocket event handlers
  // -----------------------------------------------------------------------

  private onOpen = (): void => {
    console.log('[GameConnection] WebSocket connected');
    this.setState('connected');
    this.startPing();
  };

  private onMessage = (event: MessageEvent): void => {
    if (typeof event.data !== 'string') return;

    const msg = parseServerMessage(event.data);
    if (!msg) {
      console.warn('[GameConnection] Failed to parse server message:', event.data);
      return;
    }

    this.handleMessage(msg);
  };

  private onClose = (event: CloseEvent): void => {
    console.log('[GameConnection] WebSocket closed:', event.code, event.reason);
    this.stopPing();
    this.ws = null;

    if (!this.destroyed) {
      this.setState('disconnected');
      // If we were in the game, show an error
      if (this.state === 'joined') {
        useUIStore.getState().setError('Connection lost. Please rejoin.');
        useUIStore.getState().setScreen('home');
      }
    }
  };

  private onWsError = (event: Event): void => {
    console.error('[GameConnection] WebSocket error:', event);
    this.callbacks.onError?.('WebSocket connection error.');
  };

  // -----------------------------------------------------------------------
  // Message handling
  // -----------------------------------------------------------------------

  private handleMessage(msg: ServerMessage): void {
    switch (msg.type) {
      case ServerMessageType.WELCOME:
        this.handleWelcome(msg);
        break;
      case ServerMessageType.SNAPSHOT:
        this.handleSnapshot(msg);
        break;
      case ServerMessageType.DEATH:
        this.handleDeath(msg);
        break;
      case ServerMessageType.PONG:
        this.handlePong(msg);
        break;
      case ServerMessageType.ERROR:
        this.handleError(msg);
        break;
      case ServerMessageType.EVOLUTION_OPTIONS:
        // Phase 12
        break;
    }
  }

  private handleWelcome(msg: WelcomeMessage): void {
    console.log('[GameConnection] Welcome received:', msg.playerId, msg.nickname);
    useGameStore.getState().setLocalPlayerId(msg.playerId);
    this.setState('joined');
  }

  private handleSnapshot(msg: SnapshotMessage): void {
    // Update the game store
    useGameStore.getState().updateSnapshot(
      msg.players,
      msg.foods,
      msg.leaderboard,
      msg.tick,
      msg.gridDebug,
    );

    // Notify PixiGame for rendering
    this.callbacks.onSnapshot?.(msg);
  }

  private handleDeath(msg: DeathMessage): void {
    console.log('[GameConnection] Death:', msg.reason);
    useUIStore.getState().showDeath(
      msg.killerNickname
        ? `${msg.reason} by ${msg.killerNickname}`
        : msg.reason,
    );
  }

  private handlePong(msg: PongMessage): void {
    if (this.lastPingTime > 0) {
      this._latency = Date.now() - this.lastPingTime;
    }
  }

  private handleError(msg: ErrorServerMessage): void {
    console.warn('[GameConnection] Server error:', msg.message);
    this.callbacks.onError?.(msg.message);
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  private send(msg: object): void {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(msg));
    }
  }

  private setState(state: ConnectionState): void {
    this.state = state;
    this.callbacks.onStateChange?.(state);
  }

  private startPing(): void {
    this.stopPing();
    // Ping every 5 seconds
    this.pingTimer = setInterval(() => {
      if (this.isConnected) {
        this.sendPing();
      }
    }, 5000);
  }

  private stopPing(): void {
    if (this.pingTimer !== null) {
      clearInterval(this.pingTimer);
      this.pingTimer = null;
    }
  }
}
