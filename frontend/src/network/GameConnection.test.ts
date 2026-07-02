import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

// ---------------------------------------------------------------------------
// Environment stubs
//
// GameConnection transitively imports src/config/env.ts, which reads
// `window.location` at module-evaluation time, and it also references the
// global `WebSocket` constructor. Neither exists in the default (node) vitest
// environment, so we install lightweight stubs *before* dynamically importing
// the module under test.
// ---------------------------------------------------------------------------

/** Minimal mock WebSocket that lets tests drive lifecycle events manually. */
class MockWebSocket {
  static CONNECTING = 0;
  static OPEN = 1;
  static CLOSING = 2;
  static CLOSED = 3;

  static instances: MockWebSocket[] = [];

  readonly url: string;
  readyState = MockWebSocket.CONNECTING;

  onopen: (() => void) | null = null;
  onmessage: ((event: { data: unknown }) => void) | null = null;
  onclose: ((event: { code: number; reason: string }) => void) | null = null;
  onerror: ((event: unknown) => void) | null = null;

  sent: string[] = [];

  constructor(url: string) {
    this.url = url;
    MockWebSocket.instances.push(this);
  }

  send(data: string): void {
    this.sent.push(data);
  }

  close(): void {
    this.readyState = MockWebSocket.CLOSED;
  }

  // --- test helpers -------------------------------------------------------

  /** Simulate the socket successfully opening. */
  open(): void {
    this.readyState = MockWebSocket.OPEN;
    this.onopen?.();
  }

  /** Simulate the server/network closing the socket. */
  serverClose(code = 1006, reason = 'abnormal'): void {
    this.readyState = MockWebSocket.CLOSED;
    this.onclose?.({ code, reason });
  }

  /** Simulate an inbound text message. */
  message(data: string): void {
    this.onmessage?.({ data });
  }

  static latest(): MockWebSocket {
    return MockWebSocket.instances[MockWebSocket.instances.length - 1];
  }

  static reset(): void {
    MockWebSocket.instances = [];
  }
}

describe('GameConnection reconnect logic', () => {
  // Re-imported fresh in each test so module state does not leak.
  let GameConnection: typeof import('./GameConnection').GameConnection;

  beforeEach(async () => {
    vi.useFakeTimers();
    MockWebSocket.reset();

    // Stub browser globals the module needs at import + runtime.
    vi.stubGlobal('window', {
      location: { protocol: 'http:', host: 'localhost:5173' },
    });
    vi.stubGlobal('WebSocket', MockWebSocket as unknown as typeof WebSocket);

    // Reset module registry so env.ts re-evaluates against the stubbed window.
    vi.resetModules();
    ({ GameConnection } = await import('./GameConnection'));
  });

  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  /** Drive a connection all the way to the `joined` state. */
  function connectAndJoin(conn: InstanceType<typeof GameConnection>): MockWebSocket {
    conn.connect();
    const ws = MockWebSocket.latest();
    ws.open();
    conn.join('Tester');
    // Server acknowledges with a welcome -> state becomes 'joined'.
    ws.message(JSON.stringify({ type: 'welcome', playerId: 'p1', nickname: 'Tester' }));
    return ws;
  }

  it('reaches joined state after welcome', () => {
    const states: string[] = [];
    const conn = new GameConnection({ onStateChange: (s) => states.push(s) });

    connectAndJoin(conn);

    expect(conn.connectionState).toBe('joined');
    expect(states).toContain('connecting');
    expect(states).toContain('connected');
    expect(states).toContain('joined');
  });

  it('schedules a reconnect with backoff after an unexpected close while joined', () => {
    const states: string[] = [];
    const conn = new GameConnection({ onStateChange: (s) => states.push(s) });
    const ws = connectAndJoin(conn);

    // Unexpected drop.
    ws.serverClose();
    expect(conn.connectionState).toBe('reconnecting');

    // Backoff is 500ms * attempt; nothing should reconnect before that.
    vi.advanceTimersByTime(499);
    expect(MockWebSocket.instances).toHaveLength(1);

    // After the delay a fresh socket is created.
    vi.advanceTimersByTime(1);
    expect(MockWebSocket.instances).toHaveLength(2);
    expect(states).toContain('reconnecting');
  });

  it('gives up after maxReconnectAttempts and returns to disconnected/home', async () => {
    const conn = new GameConnection();
    const ws = connectAndJoin(conn);

    // Attempt 1
    ws.serverClose();
    vi.advanceTimersByTime(500);
    // The retry socket also fails to open, then closes from 'reconnecting'.
    MockWebSocket.latest().serverClose();
    vi.advanceTimersByTime(1000); // attempt 2 backoff
    MockWebSocket.latest().serverClose();
    vi.advanceTimersByTime(1500); // attempt 3 backoff
    MockWebSocket.latest().serverClose();

    // Fourth attempt would exceed maxReconnectAttempts (3) -> give up.
    vi.advanceTimersByTime(2000);

    expect(conn.connectionState).toBe('disconnected');

    const { useUIStore } = await import('../state/uiStore');
    expect(useUIStore.getState().screen).toBe('home');
    expect(useUIStore.getState().error).toBe('Connection lost. Please rejoin.');
  });

  it('resets the reconnect attempt counter after a successful welcome', () => {
    const conn = new GameConnection();
    const ws = connectAndJoin(conn);

    // First drop -> reconnect scheduled (attempt 1).
    ws.serverClose();
    vi.advanceTimersByTime(500);

    // The reconnect socket opens and the server welcomes again.
    const ws2 = MockWebSocket.latest();
    ws2.open();
    conn.join('Tester');
    ws2.message(JSON.stringify({ type: 'welcome', playerId: 'p1', nickname: 'Tester' }));
    expect(conn.connectionState).toBe('joined');

    // A subsequent drop should again schedule (counter was reset to 0).
    ws2.serverClose();
    expect(conn.connectionState).toBe('reconnecting');
    vi.advanceTimersByTime(500);
    expect(MockWebSocket.latest()).not.toBe(ws2);
  });

  it('does not reconnect after an explicit destroy()', () => {
    const conn = new GameConnection();
    const ws = connectAndJoin(conn);

    conn.destroy();
    expect(conn.connectionState).toBe('disconnected');

    const countBefore = MockWebSocket.instances.length;
    ws.serverClose();
    vi.advanceTimersByTime(5000);
    expect(MockWebSocket.instances).toHaveLength(countBefore);
  });

  it('does not reconnect when the close happens before joining', () => {
    const conn = new GameConnection();
    conn.connect();
    const ws = MockWebSocket.latest();
    ws.open(); // 'connected' but never joined

    ws.serverClose();
    expect(conn.connectionState).toBe('disconnected');

    vi.advanceTimersByTime(5000);
    expect(MockWebSocket.instances).toHaveLength(1);
  });
});

