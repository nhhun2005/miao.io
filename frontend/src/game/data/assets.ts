/**
 * Frontend asset manifest for the Mimope game.
 *
 * Central registry of all asset paths used by the PixiJS renderer.
 * Paths are relative to the /assets/ directory (served as static files).
 */

import { ANIMALS, type AnimalDefinition } from './animals';
import { FOODS, type FoodDefinition } from './foods';

// ---------------------------------------------------------------------------
// Asset key helpers
// ---------------------------------------------------------------------------

/** Build a PixiJS-friendly asset key for an animal skin. */
export function animalSkinKey(animalId: string): string {
  return `skin_${animalId}`;
}

/** Build a PixiJS-friendly asset key for an animal full-size preview. */
export function animalFullSizeKey(animalId: string): string {
  return `fullsize_${animalId}`;
}

/** Build a PixiJS-friendly asset key for a food image. */
export function foodImageKey(foodId: string): string {
  return `food_${foodId}`;
}

/** Build a PixiJS-friendly asset key for a food edible-variant image. */
export function foodEdibleKey(foodId: string): string {
  return `food_${foodId}_e`;
}

// ---------------------------------------------------------------------------
// Static / UI assets
// ---------------------------------------------------------------------------

export const UI_ASSETS = {
  logo: 'img/logo.png',
  settings: 'img/settings.png',
  soundOn: 'img/sound_on.png',
  soundOff: 'img/sound_off.png',
  close: 'img/x.png',
  eatSymbol: 'img/instr_eatsymbol.png',
} as const;

export const MAP_ASSETS = {
  hill: 'img/hill.png',
  rockHill: 'img/rockhill.png',
  fir: 'img/fir.png',
  fir2: 'img/fir2.png',
  healingStone: 'img/healingStone.png',
  healingStoneArctic: 'img/healingStone_arctic.png',
  healingStoneOcean: 'img/healingStone_ocean.png',
  riverCurrent: 'img/riverCurrent.png',
  riverCurrent0: 'img/riverCurrent0.png',
  riverCurrent1: 'img/riverCurrent1.png',
  wave: 'img/wave.png',
  snowball: 'img/snowball.png',
  lillyPad: 'img/lillypad.png',
  lillyFlower: 'img/lilly_fl.png',
} as const;

export const ABILITY_ASSETS = {
  backKick: 'img/ability_backkick.png',
  claw: 'img/ability_claw.png',
  crocBite: 'img/ability_crocBite.png',
  dive: 'img/ability_dive.png',
} as const;

// ---------------------------------------------------------------------------
// Manifest builder
// ---------------------------------------------------------------------------

export interface AssetEntry {
  key: string;
  path: string;
}

/**
 * Build the complete list of assets to preload for the current game session.
 *
 * The `basePath` parameter is prepended to every path so Vite / the dev server
 * can resolve them correctly (e.g. `"/assets/"`).
 */
export function buildAssetManifest(basePath: string = '/'): AssetEntry[] {
  const entries: AssetEntry[] = [];

  const normalize = (p: string) => `${basePath}${p}`;

  // Animal skins
  for (const animal of Object.values(ANIMALS) as AnimalDefinition[]) {
    entries.push({ key: animalSkinKey(animal.id), path: normalize(animal.skinPath) });
    if (animal.fullSizePath) {
      entries.push({ key: animalFullSizeKey(animal.id), path: normalize(animal.fullSizePath) });
    }
  }

  // Food images
  for (const food of Object.values(FOODS) as FoodDefinition[]) {
    entries.push({ key: foodImageKey(food.id), path: normalize(food.imagePath) });
    entries.push({ key: foodEdibleKey(food.id), path: normalize(food.edibleImagePath) });
  }

  // UI assets
  for (const [key, path] of Object.entries(UI_ASSETS)) {
    entries.push({ key: `ui_${key}`, path: normalize(path) });
  }

  // Map assets
  for (const [key, path] of Object.entries(MAP_ASSETS)) {
    entries.push({ key: `map_${key}`, path: normalize(path) });
  }

  // Ability assets
  for (const [key, path] of Object.entries(ABILITY_ASSETS)) {
    entries.push({ key: `ability_${key}`, path: normalize(path) });
  }

  return entries;
}
