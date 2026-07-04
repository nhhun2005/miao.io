import { describe, expect, it } from 'vitest';
import { existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { AI_ANIMALS, ANIMALS, ANIMAL_VARIANTS } from './animals';
import {
  UNUSED_MOPE_SKIN_COUNT,
  UNUSED_MOPE_SKINS,
  type UnusedMopeSkinEntry,
} from './unusedMopeSkins';

const currentDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(currentDir, '../../../../');
const assetsRoot = resolve(repoRoot, 'assets');

function assetExists(path: string): boolean {
  return existsSync(resolve(assetsRoot, path));
}

function pathsFor(entry: UnusedMopeSkinEntry): string[] {
  return [
    entry.skinPath,
    entry.winterSkinPath,
    entry.fullSizePath,
  ].filter((path): path is string => Boolean(path));
}

describe('unused mope skin backlog', () => {
  it('tracks exactly the current 47 unused skin IDs', () => {
    expect(UNUSED_MOPE_SKINS).toHaveLength(UNUSED_MOPE_SKIN_COUNT);
  });

  it('maps every backlog ID into gameplay, variant, or AI registries', () => {
    const implementedIds = new Set([
      ...Object.keys(ANIMALS),
      ...Object.keys(ANIMAL_VARIANTS),
      ...Object.keys(AI_ANIMALS),
    ]);

    for (const entry of UNUSED_MOPE_SKINS) {
      expect(implementedIds.has(entry.id), `${entry.id} is not implemented`).toBe(true);
    }
  });

  it('does not contain duplicate backlog IDs', () => {
    const ids = UNUSED_MOPE_SKINS.map((entry) => entry.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it('points every declared skin path at an existing asset file', () => {
    for (const entry of UNUSED_MOPE_SKINS) {
      for (const path of pathsFor(entry)) {
        expect(assetExists(path), `${entry.id} missing ${path}`).toBe(true);
      }
    }
  });

  it('uses fullsize only as an optional preview asset', () => {
    for (const entry of UNUSED_MOPE_SKINS) {
      expect(entry.skinPath.includes('/fullsize/'), `${entry.id} gameplay skin uses fullsize`).toBe(false);
    }
  });

  it('marks alt skins as replacements instead of standalone evolution animals', () => {
    const variantIds = ['crab2', 'turtle2', 'muskox2', 'pufferfish2', 'swordfish2'];

    for (const id of variantIds) {
      const entry = UNUSED_MOPE_SKINS.find((candidate) => candidate.id === id);
      expect(entry?.replacesSkinId, `${id} should replace a base skin`).toBeTruthy();
      expect(ANIMAL_VARIANTS[id], `${id} should be a cosmetic animal variant`).toBeTruthy();
    }

    expect(AI_ANIMALS.snail2.variantOf).toBe('snail');
  });
});
