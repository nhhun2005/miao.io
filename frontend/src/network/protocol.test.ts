import { describe, it, expect } from 'vitest';
import {
  PROTOCOL_VERSION,
  ClientMessageType,
  ServerMessageType,
  createJoinMessage,
  createInputMessage,
  createEvolveMessage,
  createPingMessage,
  parseServerMessage,
} from './protocol';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

describe('Protocol constants', () => {
  it('has positive protocol version', () => {
    expect(PROTOCOL_VERSION).toBeGreaterThan(0);
  });

  it('defines all client message types', () => {
    expect(ClientMessageType.JOIN).toBe('join');
    expect(ClientMessageType.INPUT).toBe('input');
    expect(ClientMessageType.EVOLVE).toBe('evolve');
    expect(ClientMessageType.PING).toBe('ping');
  });

  it('defines all server message types', () => {
    expect(ServerMessageType.WELCOME).toBe('welcome');
    expect(ServerMessageType.SNAPSHOT).toBe('snapshot');
    expect(ServerMessageType.EVOLUTION_OPTIONS).toBe('evolution_options');
    expect(ServerMessageType.DEATH).toBe('death');
    expect(ServerMessageType.PONG).toBe('pong');
    expect(ServerMessageType.ERROR).toBe('error');
  });
});

// ---------------------------------------------------------------------------
// Client message builders
// ---------------------------------------------------------------------------

describe('createJoinMessage', () => {
  it('creates a join message with nickname', () => {
    const msg = createJoinMessage('Alice');
    expect(msg.type).toBe('join');
    expect(msg.nickname).toBe('Alice');
  });
});

describe('createInputMessage', () => {
  it('creates an input message with all fields', () => {
    const before = Date.now();
    const msg = createInputMessage(42, 1.5708, 0.85, true, false);
    const after = Date.now();

    expect(msg.type).toBe('input');
    expect(msg.seq).toBe(42);
    expect(msg.angle).toBeCloseTo(1.5708);
    expect(msg.intensity).toBeCloseTo(0.85);
    expect(msg.boost).toBe(true);
    expect(msg.ability).toBe(false);
    expect(msg.timestamp).toBeGreaterThanOrEqual(before);
    expect(msg.timestamp).toBeLessThanOrEqual(after);
  });
});

describe('createEvolveMessage', () => {
  it('creates an evolve message with animalId', () => {
    const msg = createEvolveMessage('rabbit');
    expect(msg.type).toBe('evolve');
    expect(msg.animalId).toBe('rabbit');
  });
});

describe('createPingMessage', () => {
  it('creates a ping message with current timestamp', () => {
    const before = Date.now();
    const msg = createPingMessage();
    const after = Date.now();

    expect(msg.type).toBe('ping');
    expect(msg.timestamp).toBeGreaterThanOrEqual(before);
    expect(msg.timestamp).toBeLessThanOrEqual(after);
  });
});

// ---------------------------------------------------------------------------
// Server message parsing — valid messages
// ---------------------------------------------------------------------------

describe('parseServerMessage', () => {
  it('parses a welcome message', () => {
    const json = JSON.stringify({
      type: 'welcome',
      playerId: 'abc-123',
      nickname: 'Alice',
      protocolVersion: 1,
    });
    const msg = parseServerMessage(json);
    expect(msg).not.toBeNull();
    expect(msg!.type).toBe('welcome');
    if (msg?.type === 'welcome') {
      expect(msg.playerId).toBe('abc-123');
      expect(msg.nickname).toBe('Alice');
      expect(msg.protocolVersion).toBe(1);
    }
  });

  it('parses a snapshot message', () => {
    const json = JSON.stringify({
      type: 'snapshot',
      tick: 100,
      players: [
        {
          id: 'p1',
          nickname: 'Alice',
          x: 100,
          y: 200,
          radius: 22,
          angle: 1.5,
          animalId: 'mouse',
          health: 100,
          maxHealth: 100,
          xp: 0,
        },
      ],
      foods: [{ id: 'f1', foodId: 'berry', x: 300, y: 400 }],
      leaderboard: [{ nickname: 'Alice', xp: 0 }],
    });
    const msg = parseServerMessage(json);
    expect(msg).not.toBeNull();
    expect(msg!.type).toBe('snapshot');
    if (msg?.type === 'snapshot') {
      expect(msg.tick).toBe(100);
      expect(msg.players).toHaveLength(1);
      expect(msg.players[0].id).toBe('p1');
      expect(msg.foods).toHaveLength(1);
      expect(msg.foods[0].foodId).toBe('berry');
      expect(msg.leaderboard).toHaveLength(1);
    }
  });

  it('parses a snapshot message without leaderboard', () => {
    const json = JSON.stringify({
      type: 'snapshot',
      tick: 50,
      players: [],
      foods: [],
    });
    const msg = parseServerMessage(json);
    expect(msg).not.toBeNull();
    if (msg?.type === 'snapshot') {
      expect(msg.leaderboard).toEqual([]);
    }
  });

  it('parses an evolution_options message', () => {
    const json = JSON.stringify({
      type: 'evolution_options',
      options: [
        { animalId: 'rabbit', name: 'Rabbit', tier: 2 },
        { animalId: 'pig', name: 'Pig', tier: 3 },
      ],
    });
    const msg = parseServerMessage(json);
    expect(msg).not.toBeNull();
    if (msg?.type === 'evolution_options') {
      expect(msg.options).toHaveLength(2);
      expect(msg.options[0].animalId).toBe('rabbit');
      expect(msg.options[1].tier).toBe(3);
    }
  });

  it('parses a death message with killer', () => {
    const json = JSON.stringify({
      type: 'death',
      reason: 'eaten',
      killerNickname: 'ProPlayer',
      xpEarned: 450,
      survivalTimeMs: 120000,
    });
    const msg = parseServerMessage(json);
    expect(msg).not.toBeNull();
    if (msg?.type === 'death') {
      expect(msg.reason).toBe('eaten');
      expect(msg.killerNickname).toBe('ProPlayer');
      expect(msg.xpEarned).toBe(450);
      expect(msg.survivalTimeMs).toBe(120000);
    }
  });

  it('parses a death message without killer', () => {
    const json = JSON.stringify({
      type: 'death',
      reason: 'timeout',
      xpEarned: 0,
      survivalTimeMs: 60000,
    });
    const msg = parseServerMessage(json);
    expect(msg).not.toBeNull();
    if (msg?.type === 'death') {
      expect(msg.killerNickname).toBeUndefined();
    }
  });

  it('parses a pong message with timestamp', () => {
    const json = JSON.stringify({ type: 'pong', timestamp: 12345 });
    const msg = parseServerMessage(json);
    expect(msg).not.toBeNull();
    if (msg?.type === 'pong') {
      expect(msg.timestamp).toBe(12345);
    }
  });

  it('parses a pong message without timestamp', () => {
    const json = JSON.stringify({ type: 'pong' });
    const msg = parseServerMessage(json);
    expect(msg).not.toBeNull();
    if (msg?.type === 'pong') {
      expect(msg.timestamp).toBeUndefined();
    }
  });

  it('parses an error message', () => {
    const json = JSON.stringify({ type: 'error', message: 'Something went wrong' });
    const msg = parseServerMessage(json);
    expect(msg).not.toBeNull();
    if (msg?.type === 'error') {
      expect(msg.message).toBe('Something went wrong');
    }
  });

  // -------------------------------------------------------------------------
  // Invalid / edge-case messages
  // -------------------------------------------------------------------------

  it('returns null for invalid JSON', () => {
    expect(parseServerMessage('not json')).toBeNull();
  });

  it('returns null for empty string', () => {
    expect(parseServerMessage('')).toBeNull();
  });

  it('returns null for JSON without type', () => {
    expect(parseServerMessage(JSON.stringify({ foo: 'bar' }))).toBeNull();
  });

  it('returns null for unknown type', () => {
    expect(parseServerMessage(JSON.stringify({ type: 'unknown_cmd' }))).toBeNull();
  });

  it('returns null for non-object JSON', () => {
    expect(parseServerMessage('"hello"')).toBeNull();
    expect(parseServerMessage('42')).toBeNull();
    expect(parseServerMessage('null')).toBeNull();
    expect(parseServerMessage('true')).toBeNull();
  });

  it('returns null for welcome missing playerId', () => {
    const json = JSON.stringify({ type: 'welcome', nickname: 'Alice' });
    expect(parseServerMessage(json)).toBeNull();
  });

  it('returns null for snapshot missing tick', () => {
    const json = JSON.stringify({ type: 'snapshot', players: [], foods: [] });
    expect(parseServerMessage(json)).toBeNull();
  });

  it('returns null for snapshot missing players', () => {
    const json = JSON.stringify({ type: 'snapshot', tick: 1, foods: [] });
    expect(parseServerMessage(json)).toBeNull();
  });

  it('returns null for evolution_options missing options', () => {
    const json = JSON.stringify({ type: 'evolution_options' });
    expect(parseServerMessage(json)).toBeNull();
  });

  it('returns null for death missing reason', () => {
    const json = JSON.stringify({ type: 'death', xpEarned: 0 });
    expect(parseServerMessage(json)).toBeNull();
  });

  it('returns null for error missing message', () => {
    const json = JSON.stringify({ type: 'error' });
    expect(parseServerMessage(json)).toBeNull();
  });

  // -------------------------------------------------------------------------
  // Serialization round-trip
  // -------------------------------------------------------------------------

  it('client messages serialize to valid JSON', () => {
    const messages = [
      createJoinMessage('TestPlayer'),
      createInputMessage(1, 0.5, 1.0, false, false),
      createEvolveMessage('fox'),
      createPingMessage(),
    ];

    for (const msg of messages) {
      const json = JSON.stringify(msg);
      const parsed = JSON.parse(json);
      expect(parsed.type).toBe(msg.type);
    }
  });
});
