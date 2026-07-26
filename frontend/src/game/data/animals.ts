export type AnimalBiome = 'land' | 'ocean' | 'arctic' | 'final';

export interface AnimalDefinition {
  id: string;
  name: string;
  tier: number;
  speed: number;
  radius: number;
  maxHealth: number;
  xpRequired: number;
  biome: AnimalBiome;
  normalEvolution: boolean;
  canEat: string[];
  skinPath: string;
  fullSizePath: string | null;
}

export function maxHealthForTier(tier: number): number {
  switch (tier) {
    case 1: return 2;
    case 2: return 3;
    case 3: return 4;
    case 4: return 5;
    case 5: return 6;
    case 6: return 7;
    case 7: return 8;
    case 8: return 9;
    case 9: return 10;
    case 10: return 11;
    case 11: return 12;
    case 12: return 13;
    case 13: return 14;
    case 14: return 16;
    case 15: return 20;
    default: return 2;
  }
}

function animal(
  id: string,
  name: string,
  tier: number,
  speed: number,
  radius: number,
  maxHealth: number,
  xpRequired: number,
  biome: AnimalBiome,
  skinPath: string,
  fullSizePath: string | null = null,
  normalEvolution = true,
): AnimalDefinition {
  return {
    id,
    name,
    tier,
    speed,
    radius,
    maxHealth: maxHealthForTier(tier),
    xpRequired,
    biome,
    normalEvolution,
    canEat: [],
    skinPath,
    fullSizePath,
  };
}

export const ANIMALS: Record<string, AnimalDefinition> = {
  mouse: animal('mouse', 'Mouse', 1, 200, 22, 100, 0, 'land', 'skins/mouse.png', 'skins/fullsize/mouse.png'),
  shrimp: animal('shrimp', 'Shrimp', 1, 205, 20, 95, 0, 'ocean', 'skins/shrimp.png', 'skins/fullsize/shrimp.png'),
  chipmunk: animal('chipmunk', 'Chipmunk', 1, 198, 21, 100, 0, 'arctic', 'skins/arctic/chipmunk.png'),
  lemming: animal('lemming', 'Lemming', 1, 190, 19, 80, 0, 'arctic', 'skins/arctic/lemming.png'),
  rabbit: animal('rabbit', 'Rabbit', 2, 190, 28, 150, 50, 'land', 'skins/rabbit.png', 'skins/fullsize/rabbit.png'),
  arctichare: animal('arctichare', 'Arctic Hare', 2, 188, 27, 150, 50, 'arctic', 'skins/arctic/arctichare.png'),
  trout: animal('trout', 'Trout', 2, 196, 26, 140, 50, 'ocean', 'skins/trout.png', 'skins/fullsize/trout.png'),
  mole: animal('mole', 'Mole', 3, 178, 32, 190, 200, 'land', 'skins/mole.png', 'skins/fullsize/mole.png'),
  crab: animal('crab', 'Crab', 3, 170, 34, 220, 200, 'ocean', 'skins/crab.png', 'skins/fullsize/crab.png'),
  penguin: animal('penguin', 'Penguin', 3, 182, 31, 185, 200, 'arctic', 'skins/arctic/penguin.png'),
  pig: animal('pig', 'Pig', 4, 175, 34, 200, 500, 'land', 'skins/pig.png', 'skins/fullsize/pig.png'),
  seahorse: animal('seahorse', 'Seahorse', 4, 185, 33, 205, 500, 'ocean', 'skins/seahorse.png'),
  seal: animal('seal', 'Seal', 4, 180, 35, 220, 500, 'arctic', 'skins/arctic/seal.png'),
  deer: animal('deer', 'Deer', 5, 180, 44, 450, 1000, 'land', 'skins/deer.png', 'skins/fullsize/deer.png'),
  squid: animal('squid', 'Squid', 5, 174, 40, 360, 1000, 'ocean', 'skins/squid.png', 'skins/fullsize/squid.png'),
  reindeer: animal('reindeer', 'Reindeer', 5, 176, 42, 430, 1000, 'arctic', 'skins/arctic/reindeer.png'),
  fox: animal('fox', 'Fox', 6, 185, 38, 300, 2000, 'land', 'skins/fox.png', 'skins/fullsize/fox.png'),
  jellyfish: animal('jellyfish', 'Jellyfish', 6, 165, 42, 380, 2000, 'ocean', 'skins/jellyfish.png'),
  arcticfox: animal('arcticfox', 'Arctic Fox', 6, 182, 38, 320, 2000, 'arctic', 'skins/arctic/arcticfox.png'),
  zebra: animal('zebra', 'Zebra', 7, 176, 46, 520, 4000, 'land', 'skins/zebra.png', 'skins/fullsize/zebra.png'),
  donkey: animal('donkey', 'Donkey', 7, 172, 46, 560, 4000, 'land', 'skins/donkey.png'),
  turtle: animal('turtle', 'Turtle', 7, 158, 48, 700, 4000, 'ocean', 'skins/turtle.png'),
  muskox: animal('muskox', 'Musk Ox', 7, 168, 48, 620, 4000, 'arctic', 'skins/arctic/muskox.png'),
  cheetah: animal('cheetah', 'Cheetah', 8, 205, 48, 580, 8000, 'land', 'skins/cheetah.png', 'skins/fullsize/cheetah.png'),
  stingray: animal('stingray', 'Stingray', 8, 176, 50, 620, 8000, 'ocean', 'skins/stingray.png'),
  wolf: animal('wolf', 'Wolf', 8, 186, 49, 600, 8000, 'arctic', 'skins/arctic/wolf.png'),
  gorilla: animal('gorilla', 'Gorilla', 9, 166, 54, 760, 16000, 'land', 'skins/gorilla.png'),
  pufferfish: animal('pufferfish', 'Pufferfish', 9, 160, 52, 760, 16000, 'ocean', 'skins/pufferfish.png'),
  snowleopard: animal('snowleopard', 'Snow Leopard', 9, 195, 52, 690, 16000, 'arctic', 'skins/arctic/snowleopard.png'),
  bear: animal('bear', 'Bear', 10, 160, 58, 900, 32000, 'land', 'skins/bear.png', 'skins/fullsize/bear.png'),
  swordfish: animal('swordfish', 'Swordfish', 10, 188, 56, 820, 32000, 'ocean', 'skins/swordfish.png'),
  walrus: animal('walrus', 'Walrus', 10, 154, 60, 980, 32000, 'arctic', 'skins/arctic/walrus.png'),
  lion: animal('lion', 'Lion', 11, 170, 60, 950, 64000, 'land', 'skins/lion.png', 'skins/fullsize/lion.png'),
  croc: animal('croc', 'Crocodile', 11, 158, 62, 1100, 64000, 'land', 'skins/croc.png', 'skins/fullsize/croc.png'),
  octopus: animal('octopus', 'Octopus', 11, 162, 62, 980, 64000, 'ocean', 'skins/octopus.png'),
  polarbear: animal('polarbear', 'Polar Bear', 11, 158, 63, 1120, 64000, 'arctic', 'skins/arctic/polarbear.png'),
  rhino: animal('rhino', 'Rhino', 12, 166, 66, 1250, 125000, 'land', 'skins/rhino.png', 'skins/fullsize/rhino.png'),
  shark: animal('shark', 'Shark', 12, 184, 64, 1180, 125000, 'ocean', 'skins/shark.png', 'skins/fullsize/shark.png'),
  wolverine: animal('wolverine', 'Wolverine', 12, 174, 62, 1120, 125000, 'arctic', 'skins/arctic/wolverine.png'),
  hippo: animal('hippo', 'Hippo', 13, 152, 72, 1550, 250000, 'land', 'skins/hippo.png', 'skins/fullsize/hippo.png'),
  killerwhale: animal('killerwhale', 'Killer Whale', 13, 176, 74, 1450, 250000, 'ocean', 'skins/killerwhale.png'),
  mammoth: animal('mammoth', 'Mammoth', 13, 145, 82, 1900, 250000, 'arctic', 'skins/arctic/mammoth.png'),
  dragon: animal('dragon', 'Dragon', 14, 162, 88, 2300, 500000, 'land', 'skins/dragon.png', 'skins/fullsize/dragon.png'),
  kraken: animal('kraken', 'Kraken', 14, 150, 90, 2400, 500000, 'ocean', 'skins/kraken.png', 'skins/fullsize/kraken.png'),
  yeti: animal('yeti', 'Yeti', 14, 154, 88, 2350, 500000, 'arctic', 'skins/arctic/yeti.png'),
  blackdragon: animal('blackdragon', 'Black Dragon', 15, 156, 105, 3500, 1000000, 'final', 'skins/blackdragon.png', null, false),
};

export const STARTER_ANIMAL_IDS = ['mouse', 'shrimp', 'chipmunk'] as const;

export const ANIMAL_TIERS: string[] = Object.values(ANIMALS)
  .filter((a) => a.normalEvolution)
  .sort((a, b) => a.tier - b.tier || a.name.localeCompare(b.name))
  .map((a) => a.id);

export function getEvolutionOptions(currentAnimalId: string): AnimalDefinition[] {
  const current = ANIMALS[currentAnimalId];
  if (!current) return [];
  const nextTier = Math.min(
    ...Object.values(ANIMALS)
      .filter((animal) => animal.normalEvolution && animal.tier > current.tier)
      .map((animal) => animal.tier),
  );
  if (!Number.isFinite(nextTier)) return [];
  return Object.values(ANIMALS)
    .filter((animal) => animal.normalEvolution && animal.tier === nextTier)
    .sort((a, b) => a.biome.localeCompare(b.biome) || a.name.localeCompare(b.name));
}

export function getAnimalPreviewPath(animalId: string): string | null {
  const animal = ANIMALS[animalId];
  if (!animal) return null;
  return animal.fullSizePath ?? animal.skinPath;
}
