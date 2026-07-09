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
  abilityId: string;
  abilityName: string;
  normalEvolution: boolean;
  canEat: string[];
  skinPath: string;
  winterSkinPath?: string;
  fullSizePath: string | null;
}

export interface AnimalVariantDefinition {
  id: string;
  baseAnimalId: string;
  rollRate: number;
  skinPath: string;
  winterSkinPath?: string;
}

export interface AiAnimalDefinition {
  id: string;
  spawnWeight: number;
  variantOf?: string;
  variantRollRate?: number;
  skinPath: string;
}

const ABILITY_NAMES: Record<string, string> = {
  dash: 'Dash',
  burrow_dash: 'Burrow Dash',
  dig_dash: 'Dig Dash',
  shell_guard: 'Shell Guard',
  ice_slide: 'Ice Slide',
  stink_dash: 'Stink Dash',
  forage_dash: 'Forage Dash',
  ink_dash: 'Ink Dash',
  sting_pulse: 'Sting Pulse',
  back_kick: 'Back Kick',
  charge: 'Charge',
  shock_pulse: 'Shock Pulse',
  inflate_guard: 'Inflate Guard',
  claw: 'Claw',
  croc_bite: 'Croc Bite',
  roar_pulse: 'Roar',
  wave_pulse: 'Wave Pulse',
  snowball_dash: 'Snowball',
  fire_dash: 'Fire Dash',
  whirlpool_pulse: 'Whirlpool',
  freeze_pulse: 'Freeze Pulse',
};

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
  abilityId: string,
  skinPath: string,
  fullSizePath: string | null = null,
  winterSkinPath?: string,
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
    abilityId,
    abilityName: ABILITY_NAMES[abilityId] ?? abilityId,
    normalEvolution,
    canEat: [],
    skinPath,
    winterSkinPath,
    fullSizePath,
  };
}

export const ANIMALS: Record<string, AnimalDefinition> = {
  mouse: animal('mouse', 'Mouse', 1, 200, 22, 100, 0, 'land', 'dash', 'skins/mouse.png', 'skins/fullsize/mouse.png', 'skins/winter/mouse.png'),
  shrimp: animal('shrimp', 'Shrimp', 1, 205, 20, 95, 0, 'ocean', 'dash', 'skins/shrimp.png', 'skins/fullsize/shrimp.png', 'skins/winter/shrimp.png'),
  chipmunk: animal('chipmunk', 'Chipmunk', 1, 198, 21, 100, 0, 'arctic', 'dash', 'skins/arctic/chipmunk.png'),
  lemming: animal('lemming', 'Lemming', 1, 190, 19, 80, 0, 'arctic', 'dash', 'skins/arctic/lemming.png'),
  rabbit: animal('rabbit', 'Rabbit', 2, 190, 28, 150, 50, 'land', 'burrow_dash', 'skins/rabbit.png', 'skins/fullsize/rabbit.png', 'skins/winter/rabbit.png'),
  arctichare: animal('arctichare', 'Arctic Hare', 2, 188, 27, 150, 50, 'arctic', 'burrow_dash', 'skins/arctic/arctichare.png'),
  trout: animal('trout', 'Trout', 2, 196, 26, 140, 50, 'ocean', 'dash', 'skins/trout.png', 'skins/fullsize/trout.png', 'skins/winter/trout.png'),
  mole: animal('mole', 'Mole', 3, 178, 32, 190, 200, 'land', 'dig_dash', 'skins/mole.png', 'skins/fullsize/mole.png', 'skins/winter/mole.png'),
  crab: animal('crab', 'Crab', 3, 170, 34, 220, 200, 'ocean', 'shell_guard', 'skins/crab.png', 'skins/fullsize/crab.png', 'skins/winter/crab.png'),
  penguin: animal('penguin', 'Penguin', 3, 182, 31, 185, 200, 'arctic', 'ice_slide', 'skins/arctic/penguin.png'),
  pig: animal('pig', 'Pig', 4, 175, 34, 200, 500, 'land', 'stink_dash', 'skins/pig.png', 'skins/fullsize/pig.png', 'skins/winter/pig.png'),
  seahorse: animal('seahorse', 'Seahorse', 4, 185, 33, 205, 500, 'ocean', 'dash', 'skins/seahorse.png', null, 'skins/winter/seahorse.png'),
  seal: animal('seal', 'Seal', 4, 180, 35, 220, 500, 'arctic', 'ice_slide', 'skins/arctic/seal.png'),
  deer: animal('deer', 'Deer', 5, 180, 44, 450, 1000, 'land', 'forage_dash', 'skins/deer.png', 'skins/fullsize/deer.png', 'skins/winter/deer.png'),
  squid: animal('squid', 'Squid', 5, 174, 40, 360, 1000, 'ocean', 'ink_dash', 'skins/squid.png', 'skins/fullsize/squid.png', 'skins/winter/squid.png'),
  reindeer: animal('reindeer', 'Reindeer', 5, 176, 42, 430, 1000, 'arctic', 'dash', 'skins/arctic/reindeer.png'),
  fox: animal('fox', 'Fox', 6, 185, 38, 300, 2000, 'land', 'dash', 'skins/fox.png', 'skins/fullsize/fox.png', 'skins/winter/fox.png'),
  jellyfish: animal('jellyfish', 'Jellyfish', 6, 165, 42, 380, 2000, 'ocean', 'sting_pulse', 'skins/jellyfish.png', null, 'skins/winter/jellyfish.png'),
  arcticfox: animal('arcticfox', 'Arctic Fox', 6, 182, 38, 320, 2000, 'arctic', 'dash', 'skins/arctic/arcticfox.png'),
  zebra: animal('zebra', 'Zebra', 7, 176, 46, 520, 4000, 'land', 'back_kick', 'skins/zebra.png', 'skins/fullsize/zebra.png', 'skins/winter/zebra.png'),
  donkey: animal('donkey', 'Donkey', 7, 172, 46, 560, 4000, 'land', 'back_kick', 'skins/donkey.png'),
  turtle: animal('turtle', 'Turtle', 7, 158, 48, 700, 4000, 'ocean', 'shell_guard', 'skins/turtle.png', null, 'skins/winter/turtle.png'),
  muskox: animal('muskox', 'Musk Ox', 7, 168, 48, 620, 4000, 'arctic', 'charge', 'skins/arctic/muskox.png'),
  cheetah: animal('cheetah', 'Cheetah', 8, 205, 48, 580, 8000, 'land', 'dash', 'skins/cheetah.png', 'skins/fullsize/cheetah.png', 'skins/winter/cheetah.png'),
  stingray: animal('stingray', 'Stingray', 8, 176, 50, 620, 8000, 'ocean', 'shock_pulse', 'skins/stingray.png', null, 'skins/winter/stingray.png'),
  wolf: animal('wolf', 'Wolf', 8, 186, 49, 600, 8000, 'arctic', 'dash', 'skins/arctic/wolf.png'),
  gorilla: animal('gorilla', 'Gorilla', 9, 166, 54, 760, 16000, 'land', 'claw', 'skins/gorilla.png', null, 'skins/winter/gorilla.png'),
  pufferfish: animal('pufferfish', 'Pufferfish', 9, 160, 52, 760, 16000, 'ocean', 'inflate_guard', 'skins/pufferfish.png', null, 'skins/winter/pufferfish.png'),
  snowleopard: animal('snowleopard', 'Snow Leopard', 9, 195, 52, 690, 16000, 'arctic', 'dash', 'skins/arctic/snowleopard.png'),
  bear: animal('bear', 'Bear', 10, 160, 58, 900, 32000, 'land', 'claw', 'skins/bear.png', 'skins/fullsize/bear.png', 'skins/winter/bear.png'),
  swordfish: animal('swordfish', 'Swordfish', 10, 188, 56, 820, 32000, 'ocean', 'charge', 'skins/swordfish.png', null, 'skins/winter/swordfish.png'),
  walrus: animal('walrus', 'Walrus', 10, 154, 60, 980, 32000, 'arctic', 'shell_guard', 'skins/arctic/walrus.png'),
  lion: animal('lion', 'Lion', 11, 170, 60, 950, 64000, 'land', 'roar_pulse', 'skins/lion.png', 'skins/fullsize/lion.png', 'skins/winter/lion.png'),
  croc: animal('croc', 'Crocodile', 11, 158, 62, 1100, 64000, 'land', 'croc_bite', 'skins/croc.png', 'skins/fullsize/croc.png', 'skins/winter/croc.png'),
  octopus: animal('octopus', 'Octopus', 11, 162, 62, 980, 64000, 'ocean', 'ink_dash', 'skins/octopus.png', null, 'skins/winter/octopus.png'),
  polarbear: animal('polarbear', 'Polar Bear', 11, 158, 63, 1120, 64000, 'arctic', 'claw', 'skins/arctic/polarbear.png'),
  rhino: animal('rhino', 'Rhino', 12, 166, 66, 1250, 125000, 'land', 'charge', 'skins/rhino.png', 'skins/fullsize/rhino.png', 'skins/winter/rhino.png'),
  shark: animal('shark', 'Shark', 12, 184, 64, 1180, 125000, 'ocean', 'charge', 'skins/shark.png', 'skins/fullsize/shark.png', 'skins/winter/shark.png'),
  wolverine: animal('wolverine', 'Wolverine', 12, 174, 62, 1120, 125000, 'arctic', 'claw', 'skins/arctic/wolverine.png'),
  hippo: animal('hippo', 'Hippo', 13, 152, 72, 1550, 250000, 'land', 'roar_pulse', 'skins/hippo.png', 'skins/fullsize/hippo.png', 'skins/winter/hippo.png'),
  killerwhale: animal('killerwhale', 'Killer Whale', 13, 176, 74, 1450, 250000, 'ocean', 'wave_pulse', 'skins/killerwhale.png', null, 'skins/winter/killerwhale.png'),
  mammoth: animal('mammoth', 'Mammoth', 13, 145, 82, 1900, 250000, 'arctic', 'snowball_dash', 'skins/arctic/mammoth.png'),
  dragon: animal('dragon', 'Dragon', 14, 162, 88, 2300, 500000, 'land', 'fire_dash', 'skins/dragon.png', 'skins/fullsize/dragon.png', 'skins/winter/dragon.png'),
  kraken: animal('kraken', 'Kraken', 14, 150, 90, 2400, 500000, 'ocean', 'whirlpool_pulse', 'skins/kraken.png', 'skins/fullsize/kraken.png', 'skins/winter/kraken.png'),
  yeti: animal('yeti', 'Yeti', 14, 154, 88, 2350, 500000, 'arctic', 'freeze_pulse', 'skins/arctic/yeti.png'),
  blackdragon: animal('blackdragon', 'Black Dragon', 15, 156, 105, 3500, 1000000, 'final', 'fire_dash', 'skins/blackdragon.png', null, undefined, false),
};

export const ANIMAL_VARIANTS: Record<string, AnimalVariantDefinition> = {
  crab2: { id: 'crab2', baseAnimalId: 'crab', rollRate: 0.1, skinPath: 'skins/crab2.png', winterSkinPath: 'skins/winter/crab2.png' },
  turtle2: { id: 'turtle2', baseAnimalId: 'turtle', rollRate: 0.1, skinPath: 'skins/turtle2.png', winterSkinPath: 'skins/winter/turtle2.png' },
  muskox2: { id: 'muskox2', baseAnimalId: 'muskox', rollRate: 0.1, skinPath: 'skins/arctic/muskox2.png' },
  pufferfish2: { id: 'pufferfish2', baseAnimalId: 'pufferfish', rollRate: 0.1, skinPath: 'skins/pufferfish2.png', winterSkinPath: 'skins/winter/pufferfish2.png' },
  swordfish2: { id: 'swordfish2', baseAnimalId: 'swordfish', rollRate: 0.1, skinPath: 'skins/swordfish2.png', winterSkinPath: 'skins/winter/swordfish2.png' },
};

export const AI_ANIMALS: Record<string, AiAnimalDefinition> = {
  snail: { id: 'snail', spawnWeight: 8, skinPath: 'skins/snail.png' },
  snail2: { id: 'snail2', spawnWeight: 0, variantOf: 'snail', variantRollRate: 0.1, skinPath: 'skins/snail2.png' },
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
