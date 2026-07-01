/**
 * Animal definitions for the Mimope game.
 *
 * IDs must match the backend AnimalDefinition enum values exactly.
 * Tier determines evolution order; higher tier eats lower tier.
 */

export interface AnimalDefinition {
  /** Unique identifier — must match backend */
  id: string;
  /** Display name shown in UI */
  name: string;
  /** Evolution tier (1 = starter, higher = stronger) */
  tier: number;
  /** Base movement speed (pixels per second at standard scale) */
  speed: number;
  /** Base radius (pixels at standard scale) */
  radius: number;
  /** Maximum health points */
  maxHealth: number;
  /** XP required to reach this tier (0 for tier 1) */
  xpRequired: number;
  /** Biome where this animal spawns */
  biome: 'land' | 'ocean' | 'arctic';
  /** IDs of animals this animal can eat */
  canEat: string[];
  /** Skin sprite path relative to /assets/ */
  skinPath: string;
  /** Full-size preview path relative to /assets/, or null */
  fullSizePath: string | null;
}

/**
 * First playable animal set (Phase 2, Tier 1–6, land biome only).
 */
export const ANIMALS: Record<string, AnimalDefinition> = {
  mouse: {
    id: 'mouse',
    name: 'Mouse',
    tier: 1,
    speed: 200,
    radius: 22,
    maxHealth: 100,
    xpRequired: 0,
    biome: 'land',
    canEat: [],
    skinPath: 'skins/mouse.png',
    fullSizePath: 'skins/fullsize/mouse.png',
  },
  rabbit: {
    id: 'rabbit',
    name: 'Rabbit',
    tier: 2,
    speed: 190,
    radius: 28,
    maxHealth: 150,
    xpRequired: 50,
    biome: 'land',
    canEat: ['mouse'],
    skinPath: 'skins/rabbit.png',
    fullSizePath: 'skins/fullsize/rabbit.png',
  },
  pig: {
    id: 'pig',
    name: 'Pig',
    tier: 3,
    speed: 175,
    radius: 34,
    maxHealth: 200,
    xpRequired: 200,
    biome: 'land',
    canEat: ['mouse', 'rabbit'],
    skinPath: 'skins/pig.png',
    fullSizePath: 'skins/fullsize/pig.png',
  },
  fox: {
    id: 'fox',
    name: 'Fox',
    tier: 4,
    speed: 185,
    radius: 38,
    maxHealth: 300,
    xpRequired: 500,
    biome: 'land',
    canEat: ['mouse', 'rabbit', 'pig'],
    skinPath: 'skins/fox.png',
    fullSizePath: 'skins/fullsize/fox.png',
  },
  deer: {
    id: 'deer',
    name: 'Deer',
    tier: 5,
    speed: 180,
    radius: 44,
    maxHealth: 450,
    xpRequired: 1000,
    biome: 'land',
    canEat: ['mouse', 'rabbit', 'pig', 'fox'],
    skinPath: 'skins/deer.png',
    fullSizePath: 'skins/fullsize/deer.png',
  },
  lion: {
    id: 'lion',
    name: 'Lion',
    tier: 6,
    speed: 170,
    radius: 50,
    maxHealth: 600,
    xpRequired: 2000,
    biome: 'land',
    canEat: ['mouse', 'rabbit', 'pig', 'fox', 'deer'],
    skinPath: 'skins/lion.png',
    fullSizePath: 'skins/fullsize/lion.png',
  },
};

/** Ordered list of animal IDs by tier (ascending). */
export const ANIMAL_TIERS: string[] = Object.values(ANIMALS)
  .sort((a, b) => a.tier - b.tier)
  .map((a) => a.id);

/** Get possible evolution targets for a given animal. */
export function getEvolutionOptions(currentAnimalId: string): AnimalDefinition[] {
  const current = ANIMALS[currentAnimalId];
  if (!current) return [];
  return Object.values(ANIMALS).filter((a) => a.tier === current.tier + 1);
}
