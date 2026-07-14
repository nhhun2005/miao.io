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
  createEvolveMessage,
  createGridDebugMessage,
  createDebugLevelUpMessage,
  createPingMessage,
  parseServerMessage,
  ServerMessageType,
  type ServerMessage,
  type SnapshotMessage,
  type WelcomeMessage,
  type DeathMessage,
  type ErrorServerMessage,
} from './protocol';
import { useGameStore } from '../state/gameStore';
import { useUIStore } from '../state/uiStore';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export type ConnectionState = 'disconnected' | 'connecting' | 'reconnecting' | 'connected' | 'joined';

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
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private lastPingTime = 0;
  private _latency = 0;
  private destroyed = false;
  private lastNickname: string | null = null;
  private reconnectAttempts = 0;
  private readonly maxReconnectAttempts = 3;
  private died = false;


  constructor(callbacks: GameConnectionCallbacks = {}) {
    this.callbacks = callbacks;
  }

  // -----------------------------------------------------------------------
  // Public API
  // -----------------------------------------------------------------------

  /** Connect to the game server WebSocket. */
  connect(reconnecting = false): void {
    if (this.destroyed) return;
    if (this.ws) {
      this.disconnect();
    }

    this.setState(reconnecting ? 'reconnecting' : 'connecting');

    try {
      this.ws = new WebSocket(WS_URL);
    } catch (err) {
      console.error('[GameConnection] Failed to create WebSocket:', err);
      if (reconnecting) {
        this.scheduleReconnect();
      } else {
        this.setState('disconnected');
        this.callbacks.onError?.('Failed to connect to server.');
      }
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
    this.stopReconnect();
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
  join(nickname: string, starterAnimalId?: string): void {
    this.lastNickname = nickname;
    this.send(createJoinMessage(nickname, starterAnimalId));
  }

  /** Send an input message. */
  sendInput(seq: number, angle: number, intensity: number, boost: boolean, ability: boolean): void {
    this.send(createInputMessage(seq, angle, intensity, boost, ability));
  }

  /** Send an evolve request. */
  sendEvolve(animalId: string): void {
    this.send(createEvolveMessage(animalId));
  }

  /** Send grid debug toggle message. */
  sendGridDebugToggle(enabled: boolean): void {
    this.send(createGridDebugMessage(enabled));
  }

  /** Debug-only: request an instant level-up to the next tier. */
  sendDebugLevelUp(): void {
    this.send(createDebugLevelUpMessage());
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
    const previousState = this.state;

    if (!this.destroyed) {
      if (this.died) {
        // The server closed the socket after killing this player. This is an
        // expected close, not a network failure — do not attempt to reconnect.
        this.setState('disconnected');
      } else if (previousState === 'joined' || previousState === 'reconnecting') {
        this.scheduleReconnect();
      } else {
        this.setState('disconnected');
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
        this.handlePong();
        break;
      case ServerMessageType.ERROR:
        this.handleError(msg);
        break;
      case ServerMessageType.EVOLUTION_OPTIONS:
        useGameStore.getState().setEvolutionOptions(msg.options);
        break;
    }
  }

  private handleWelcome(msg: WelcomeMessage): void {
    console.log('[GameConnection] Welcome received:', msg.playerId, msg.nickname);
    useGameStore.getState().setLocalPlayerId(msg.playerId);
    this.reconnectAttempts = 0;
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
    // Mark this as an expected close so a subsequent socket close does not
    // trigger reconnect attempts or a "failed to connect" error.
    this.died = true;
    useUIStore.getState().showDeath(

      msg.killerNickname
        ? `${msg.reason} by ${msg.killerNickname}`
        : msg.reason,
    );
  }

  private handlePong(): void {
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

  private scheduleReconnect(): void {
    if (this.destroyed || this.reconnectTimer) return;
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      this.setState('disconnected');
      useUIStore.getState().setError('Connection lost. Please rejoin.');
      useUIStore.getState().setScreen('home');
      return;
    }

    this.reconnectAttempts += 1;
    this.setState('reconnecting');
    const delayMs = 500 * this.reconnectAttempts;
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.connect(true);
    }, delayMs);
  }

  private stopReconnect(): void {
    if (this.reconnectTimer !== null) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }
}
