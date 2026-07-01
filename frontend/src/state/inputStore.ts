/**
 * inputStore — Zustand store for exposing input state to React components.
 *
 * The InputManager (owned by PixiGame) pushes updates into this store
 * at a throttled rate so that React HUD components can display the
 * current angle, boost status, ability cooldown, etc.
 *
 * This store is READ-ONLY from the React side. Writes come only from
 * InputManager via the `syncFromInput` action.
 */

import { create } from 'zustand';

export interface InputState {
  /** Movement angle in radians (0 = right, PI/2 = down). */
  angle: number;
  /** Movement intensity 0–1 (distance from center, normalised). */
  intensity: number;
  /** Whether boost is currently held. */
  boost: boolean;
  /** Whether ability was triggered this frame. */
  ability: boolean;
  /** Current input sequence number. */
  seq: number;
  /** Whether the window is focused. */
  focused: boolean;
  /** Raw pointer offset from canvas center (px). */
  pointerX: number;
  pointerY: number;

  /** Sync state from InputManager — called at throttled rate. */
  syncFromInput: (data: {
    angle: number;
    intensity: number;
    boost: boolean;
    ability: boolean;
    seq: number;
    focused: boolean;
    pointerX: number;
    pointerY: number;
  }) => void;

  /** Reset to defaults. */
  reset: () => void;
}

const initialState = {
  angle: 0,
  intensity: 0,
  boost: false,
  ability: false,
  seq: 0,
  focused: true,
  pointerX: 0,
  pointerY: 0,
};

export const useInputStore = create<InputState>((set) => ({
  ...initialState,

  syncFromInput: (data) => set(data),

  reset: () => set(initialState),
}));
