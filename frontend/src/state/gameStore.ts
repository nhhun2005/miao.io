import { create } from "zustand";
import type { EvolutionOption } from "../network/protocol";

/** Minimal player data received in snapshots. */
export interface PlayerSnapshot {
  id: string;
  nickname: string;
  x: number;
  y: number;
  radius: number;
  angle: number;
  animalId: string;
  health: number;
  maxHealth: number;
  xp: number;
  oceanSurvival?: number;
  maxOceanSurvival?: number;
  dashCooldownTicks: number;
}

/** Minimal food data received in snapshots. */
export interface FoodSnapshot {
  id: string;
  foodId: string;
  x: number;
  y: number;
}

export interface GameState {
  /** ID assigned by the server to the local player. */
  localPlayerId: string | null;
  /** All visible players keyed by id. */
  players: Record<string, PlayerSnapshot>;
  /** All visible food items keyed by id. */
  foods: Record<string, FoodSnapshot>;
  /** Leaderboard entries (top players). */
  leaderboard: { nickname: string; xp: number }[];
  /** Server tick timestamp of the last snapshot. */
  lastSnapshotTick: number;
  /** Evolution options currently available to the local player. */
  evolutionOptions: EvolutionOption[];

  setLocalPlayerId: (id: string) => void;
  updateSnapshot: (
    players: PlayerSnapshot[],
    foods: FoodSnapshot[],
    leaderboard: { nickname: string; xp: number }[],
    tick: number,
  ) => void;
  setEvolutionOptions: (options: EvolutionOption[]) => void;
  clearEvolutionOptions: () => void;
  reset: () => void;
}

const initialState = {
  localPlayerId: null as string | null,
  players: {} as Record<string, PlayerSnapshot>,
  foods: {} as Record<string, FoodSnapshot>,
  leaderboard: [] as { nickname: string; xp: number }[],
  lastSnapshotTick: 0,
  evolutionOptions: [] as EvolutionOption[],
};

export const useGameStore = create<GameState>((set) => ({
  ...initialState,

  setLocalPlayerId: (id) => set({ localPlayerId: id }),

  updateSnapshot: (players, foods, leaderboard, tick) =>
    set({
      players: Object.fromEntries(players.map((p) => [p.id, p])),
      foods: Object.fromEntries(foods.map((f) => [f.id, f])),
      leaderboard,
      lastSnapshotTick: tick,
    }),

  setEvolutionOptions: (options) => set({ evolutionOptions: options }),

  clearEvolutionOptions: () => set({ evolutionOptions: [] }),

  reset: () => set({ ...initialState }),
}));
