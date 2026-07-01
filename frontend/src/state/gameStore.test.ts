import { describe, expect, it, beforeEach } from 'vitest';
import { useGameStore } from './gameStore';

describe('gameStore', () => {
  beforeEach(() => {
    useGameStore.getState().reset();
  });

  it('stores snapshots by id', () => {
    useGameStore.getState().updateSnapshot(
      [{
        id: 'p1',
        nickname: 'Alice',
        x: 100,
        y: 200,
        radius: 22,
        angle: 0,
        animalId: 'mouse',
        health: 100,
        maxHealth: 100,
        xp: 25,
        abilityCooldownTicks: 0,
      }],
      [{ id: 'f1', foodId: 'berry', x: 300, y: 400 }],
      [{ nickname: 'Alice', xp: 25 }],
      12,
    );

    const state = useGameStore.getState();
    expect(state.players.p1.nickname).toBe('Alice');
    expect(state.foods.f1.foodId).toBe('berry');
    expect(state.leaderboard[0].xp).toBe(25);
    expect(state.lastSnapshotTick).toBe(12);
  });

  it('stores and clears evolution options', () => {
    useGameStore.getState().setEvolutionOptions([{ animalId: 'rabbit', name: 'Rabbit', tier: 2 }]);
    expect(useGameStore.getState().evolutionOptions).toHaveLength(1);

    useGameStore.getState().clearEvolutionOptions();
    expect(useGameStore.getState().evolutionOptions).toEqual([]);
  });
});
