# Mimope Mope Skin Gameplay Registry

This table started as the unused `assets/skins` backlog. It now tracks the 47 Mope skin IDs that were promoted into gameplay, cosmetic variant, or AI registries.

- Gameplay animal registry: `frontend/src/game/data/animals.ts` and `backend/src/main/java/com/mimope/server/game/data/AnimalDefinition.java`.
- Cosmetic variant registries: `ANIMAL_VARIANTS` and `AnimalVariantDefinition`.
- AI/food animal registries: `AI_ANIMALS` and `AiAnimalDefinition`.
- Promoted animal/skin IDs counted here: **47**.
- `assets/skins/fullsize` is treated as preview art only, not as separate animals.
- Winter skins are proposed seasonal cosmetics, not separate evolution animals.
- Proposed appearance rates are Mimope defaults, not exact Mope.io odds.
- Structured inventory source: `frontend/src/game/data/unusedMopeSkins.ts`.

| ID skin | Mope | Mope.io branch | Available variants | Proposed appearance rate |
|---|---|---|---|---|
| `chipmunk` | Chipmunk | Arctic T1 | arctic | 100% option |
| `shrimp` | Shrimp | Ocean T1 | base, winter, fullsize | 100%; winter 8% |
| `lemming` | Lemming | Arctic T1 / AI-compatible | arctic | 100% option or AI spawn |
| `arctichare` | Arctic Hare | Arctic T2 | arctic | 100% option |
| `trout` | Trout | Ocean T2 | base, winter, fullsize | 100%; winter 8% |
| `mole` | Mole | Land T3 | base, winter, fullsize | 100%; winter 8% |
| `crab` | Crab | Ocean T3 | base, winter, fullsize | 100%; winter 8% |
| `crab2` | Crab alt | Ocean T3 skin variant | base, winter | 10% replaces `crab` |
| `penguin` | Penguin | Arctic T3 | arctic | 100% option |
| `seahorse` | Seahorse | Ocean T4 | base, winter | 100%; winter 8% |
| `seal` | Seal | Arctic T4 | arctic | 100% option |
| `squid` | Squid | Ocean T5 | base, winter, fullsize | 100%; winter 8% |
| `reindeer` | Reindeer | Arctic T5 | arctic | 100% option |
| `jellyfish` | Jellyfish | Ocean T6 | base, winter | 100%; winter 8% |
| `arcticfox` | Arctic Fox | Arctic T6 | arctic | 100% option |
| `zebra` | Zebra | Land T7 | base, winter, fullsize | 100%; winter 8% |
| `donkey` | Donkey | Land T7 | base | 100% option |
| `turtle` | Turtle | Ocean T7 | base, winter | 100%; winter 8% |
| `turtle2` | Turtle alt | Ocean T7 skin variant | base, winter | 10% replaces `turtle` |
| `muskox` | Musk Ox | Arctic T7 | arctic | 100% option |
| `muskox2` | Musk Ox alt | Arctic T7 skin variant | arctic | 10% replaces `muskox` |
| `cheetah` | Cheetah | Land T8 | base, winter, fullsize | 100%; winter 8% |
| `stingray` | Stingray | Ocean T8 | base, winter | 100%; winter 8% |
| `wolf` | Wolf | Arctic T8 | arctic | 100% option |
| `gorilla` | Gorilla | Land T9 | base, winter | 100%; winter 8% |
| `pufferfish` | Pufferfish | Ocean T9 | base, winter | 100%; winter 8% |
| `pufferfish2` | Pufferfish alt | Ocean T9 skin variant | base, winter | 10% replaces `pufferfish` |
| `snowleopard` | Snow Leopard | Arctic T9 | arctic | 100% option |
| `bear` | Bear | Land T10 | base, winter, fullsize | 100%; winter 8% |
| `swordfish` | Swordfish | Ocean T10 | base, winter | 100%; winter 8% |
| `swordfish2` | Swordfish alt | Ocean T10 skin variant | base, winter | 10% replaces `swordfish` |
| `walrus` | Walrus | Arctic T10 | arctic | 100% option |
| `croc` | Crocodile | Land T11 | base, winter, fullsize | 100%; winter 8% |
| `octopus` | Octopus | Ocean T11 | base, winter | 100%; winter 8% |
| `polarbear` | Polar Bear | Arctic T11 | arctic | 100% option |
| `rhino` | Rhino | Land T12 | base, winter, fullsize | 100%; winter 8% |
| `shark` | Shark | Ocean T12 | base, winter, fullsize | 100%; winter 8% |
| `wolverine` | Wolverine | Arctic T12 | arctic | 100% option |
| `hippo` | Hippo | Land T13 | base, winter, fullsize | 100%; winter 8% |
| `killerwhale` | Killer Whale | Ocean T13 | base, winter | 100%; winter 8% |
| `mammoth` | Mammoth | Arctic T14 | arctic | 100% option |
| `dragon` | Dragon | Land T15 Apex | base, winter, fullsize | 100%; winter 8% |
| `kraken` | Kraken | Ocean T15 Apex | base, winter, fullsize | 100%; winter 8% |
| `yeti` | Yeti | Arctic T15 Apex | arctic | 100% option |
| `blackdragon` | Black Dragon | Final / T17 | base | unlock-only, 100% after unlock |
| `snail` | Snail | AI / food source | base | AI spawn weight 8% |
| `snail2` | Snail alt | AI skin variant | base | 10% replaces `snail` |

## Implementation notes

- Playable normal animals are in frontend/backend animal registries with tier, biome, stats, ability ID, and evolution metadata.
- `crab2`, `turtle2`, `muskox2`, `pufferfish2`, and `swordfish2` are cosmetic variants only.
- `snail` and `snail2` are outside the evolution tree and reserved for AI/food spawning.
- `blackdragon` is outside normal evolution and is exposed through the final unlock rule.
