import { create } from "zustand";

/** Screens the player can navigate between. */
export type Screen = "home" | "loading" | "game" | "death";

export interface UIState {
  /** Current active screen. */
  screen: Screen;
  /** Player nickname. */
  nickname: string;
  /** Global error message shown as a toast / banner. */
  error: string | null;
  /** Cause of death shown on the death screen. */
  deathMessage: string | null;
  starterAnimalId: string;

  setScreen: (screen: Screen) => void;
  setNickname: (nickname: string) => void;
  setStarterAnimalId: (starterAnimalId: string) => void;
  setError: (error: string | null) => void;
  showDeath: (message: string) => void;
  reset: () => void;
}

const initialState = {
  screen: "home" as Screen,
  nickname: "",
  starterAnimalId: "mouse",
  error: null as string | null,
  deathMessage: null as string | null,
};

export const useUIStore = create<UIState>((set) => ({
  ...initialState,

  setScreen: (screen) => set({ screen }),

  setNickname: (nickname) => set({ nickname }),

  setStarterAnimalId: (starterAnimalId) => set({ starterAnimalId }),

  setError: (error) => set({ error }),

  showDeath: (message) => set({ screen: "death", deathMessage: message }),

  reset: () => set({ ...initialState }),
}));
