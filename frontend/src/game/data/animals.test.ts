import { describe, expect, it } from 'vitest';
import { existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  AI_ANIMALS,
  ANIMALS,
  ANIMAL_VARIANTS,
  STARTER_ANIMAL_IDS,
  getEvolutionOptions,
} from './animals';

const currentDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(currentDir, '../../../../');
const assetsRoot = resolve(repoRoot, 'assets');

function assetExists(path: string | undefined): boolean {
  return !path || existsSync(resolve(assetsRoot, path));
}

describe('animal gameplay registry', () => {
  it('contains the full playable animal registry and starter IDs', () => {
    expect(Object.keys(ANIMALS)).toHaveLength(46);
    expect(STARTER_ANIMAL_IDS).toEqual(['mouse', 'shrimp', 'chipmunk']);
    expect(ANIMALS.blackdragon.normalEvolution).toBe(false);
  });

  it('keeps evolution options sorted by biome then name at the next available tier', () => {
    expect(getEvolutionOptions('mouse').map((animal) => animal.id)).toEqual([
      'arctichare',
      'rabbit',
      'trout',
    ]);
    expect(getEvolutionOptions('hippo').map((animal) => animal.id)).toEqual(['mammoth']);
    expect(getEvolutionOptions('mammoth').map((animal) => animal.id)).toEqual([
      'yeti',
      'dragon',
      'kraken',
    ]);
    expect(getEvolutionOptions('dragon')).toEqual([]);
  });

  it('points every gameplay, variant, and AI skin path at an existing file', () => {
    for (const animal of Object.values(ANIMALS)) {
      expect(assetExists(animal.skinPath), `${animal.id} missing skin`).toBe(true);
      expect(assetExists(animal.winterSkinPath), `${animal.id} missing winter skin`).toBe(true);
      expect(assetExists(animal.fullSizePath), `${animal.id} missing fullsize preview`).toBe(true);
    }

    for (const variant of Object.values(ANIMAL_VARIANTS)) {
      expect(assetExists(variant.skinPath), `${variant.id} missing skin`).toBe(true);
      expect(assetExists(variant.winterSkinPath), `${variant.id} missing winter skin`).toBe(true);
      expect(ANIMALS[variant.baseAnimalId], `${variant.id} missing base animal`).toBeTruthy();
    }

    for (const animal of Object.values(AI_ANIMALS)) {
      expect(assetExists(animal.skinPath), `${animal.id} missing skin`).toBe(true);
    }
  });
});
