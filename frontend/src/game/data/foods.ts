/**
 * Food definitions for the Mimope game.
 *
 * IDs must match the backend FoodDefinition enum values exactly.
 */

export interface FoodDefinition {
  /** Unique identifier — must match backend */
  id: string;
  /** Display name shown in UI */
  name: string;
  /** XP awarded when a player picks up this food */
  xp: number;
  /** Base radius of the food sprite (pixels) */
  radius: number;
  /** Minimum animal tier required to eat this food (1 = any) */
  minTier: number;
  /** Biome where this food spawns */
  biome: 'land' | 'ocean' | 'arctic';
  /** Spawn weight — higher = more common */
  spawnWeight: number;
  /** Image path relative to /assets/ */
  imagePath: string;
  /** Edible-variant image path relative to /assets/ (eaten animation) */
  edibleImagePath: string;
}

/**
 * First playable food set (Phase 2, land biome).
 */
export const FOODS: Record<string, FoodDefinition> = {
  berry: {
    id: 'berry',
    name: 'Berry',
    xp: 5,
    radius: 10,
    minTier: 1,
    biome: 'land',
    spawnWeight: 50,
    imagePath: 'img/rasp.png',
    edibleImagePath: 'img/rasp_e.png',
  },
  banana: {
    id: 'banana',
    name: 'Banana',
    xp: 15,
    radius: 14,
    minTier: 1,
    biome: 'land',
    spawnWeight: 30,
    imagePath: 'img/banana.png',
    edibleImagePath: 'img/banana_e.png',
  },
  meat: {
    id: 'meat',
    name: 'Meat',
    xp: 50,
    radius: 16,
    minTier: 3,
    biome: 'land',
    spawnWeight: 10,
    imagePath: 'img/meat.png',
    edibleImagePath: 'img/meat_e.png',
  },
  coconut: {
    id: 'coconut',
    name: 'Coconut',
    xp: 25,
    radius: 14,
    minTier: 2,
    biome: 'land',
    spawnWeight: 20,
    imagePath: 'img/coconut.png',
    edibleImagePath: 'img/coconut_e.png',
  },
  watermelon: {
    id: 'watermelon',
    name: 'Watermelon',
    xp: 80,
    radius: 20,
    minTier: 4,
    biome: 'land',
    spawnWeight: 5,
    imagePath: 'img/watermelon.png',
    edibleImagePath: 'img/watermelon_e.png',
  },
  seaweed: {
    id: 'seaweed',
    name: 'Seaweed',
    xp: 20,
    radius: 14,
    minTier: 1,
    biome: 'ocean',
    spawnWeight: 35,
    imagePath: 'img/seaweed.png',
    edibleImagePath: 'img/seaweed_e.png',
  },
  arctic_berry: {
    id: 'arctic_berry',
    name: 'Arctic Berry',
    xp: 12,
    radius: 10,
    minTier: 1,
    biome: 'arctic',
    spawnWeight: 35,
    imagePath: 'img/arcticBerry.png',
    edibleImagePath: 'img/arcticBerry_e.png',
  },
  snail: {
    id: 'snail',
    name: 'Snail',
    xp: 25,
    radius: 14,
    minTier: 1,
    biome: 'land',
    spawnWeight: 8,
    imagePath: 'skins/snail.png',
    edibleImagePath: 'skins/snail.png',
  },
  snail2: {
    id: 'snail2',
    name: 'Snail',
    xp: 25,
    radius: 14,
    minTier: 1,
    biome: 'land',
    spawnWeight: 0,
    imagePath: 'skins/snail2.png',
    edibleImagePath: 'skins/snail2.png',
  },
};

/** All food IDs as an array. */
export const FOOD_IDS: string[] = Object.keys(FOODS);
