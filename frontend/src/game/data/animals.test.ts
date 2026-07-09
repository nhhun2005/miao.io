import { describe, expect, it } from 'vitest';
import { existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  AI_ANIMALS,
  ANIMALS,
  ANIMAL_TIERS,
  ANIMAL_VARIANTS,
  STARTER_ANIMAL_IDS,
  getAnimalPreviewPath,
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
    expect(ANIMAL_TIERS).not.toContain('blackdragon');
    expect(ANIMAL_TIERS).not.toContain('snail');
    expect(ANIMAL_TIERS.some((id) => /\d$/.test(id))).toBe(false);
  });

  it('keeps evolution options sorted by biome then name at the next available tier', () => {
    expect(getEvolutionOptions('mouse').map((animal) => animal.id)).toEqual([
      'arctichare',
      'rabbit',
      'trout',
    ]);
    expect(getEvolutionOptions('fox').map((animal) => animal.id)).toEqual([
      'muskox',
      'donkey',
      'zebra',
      'turtle',
    ]);
    expect(getEvolutionOptions('hippo').map((animal) => animal.id)).toEqual([
      'yeti',
      'dragon',
      'kraken',
    ]);
    expect(getEvolutionOptions('dragon')).toEqual([]);
  });

  it('uses the final 15-tier XP plan', () => {
    const expected: Record<string, [number, number, boolean]> = {
      mammoth: [13, 250000, true],
      dragon: [14, 500000, true],
      kraken: [14, 500000, true],
      yeti: [14, 500000, true],
      blackdragon: [15, 1000000, false],
    };

    for (const [id, [tier, xpRequired, normalEvolution]] of Object.entries(expected)) {
      expect(ANIMALS[id].tier, `${id} tier`).toBe(tier);
      expect(ANIMALS[id].xpRequired, `${id} xpRequired`).toBe(xpRequired);
      expect(ANIMALS[id].normalEvolution, `${id} normalEvolution`).toBe(normalEvolution);
    }

    expect(new Set(Object.values(ANIMALS).map((animal) => animal.tier))).toEqual(
      new Set([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15]),
    );
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

  it('provides an asset-backed preview path for every evolution animal', () => {
    for (const animal of Object.values(ANIMALS)) {
      const previewPath = getAnimalPreviewPath(animal.id);

      expect(previewPath, `${animal.id} missing preview path`).toBeTruthy();
      expect(assetExists(previewPath ?? undefined), `${animal.id} preview path missing asset`).toBe(true);
    }
  });
});
