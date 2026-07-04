package com.mimope.server.game.data;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AnimalDefinition} game data.
 */
class AnimalDefinitionTest {

    @Test
    void registryContainsFullGameplayAnimalSet() {
        Map<String, AnimalDefinition> all = AnimalDefinition.all();

        assertEquals(46, all.size());
        assertNotNull(all.get("mouse"));
        assertNotNull(all.get("shrimp"));
        assertNotNull(all.get("chipmunk"));
        assertNotNull(all.get("dragon"));
        assertNotNull(all.get("kraken"));
        assertNotNull(all.get("yeti"));
        assertNotNull(all.get("blackdragon"));
    }

    @Test
    void normalEvolutionRegistryExcludesFinalUnlockOnlyAnimal() {
        List<AnimalDefinition> normalAnimals = AnimalDefinition.all().values().stream()
                .filter(AnimalDefinition::normalEvolution)
                .toList();

        assertEquals(45, normalAnimals.size());
        assertFalse(AnimalDefinition.byId("blackdragon").normalEvolution());
    }

    @Test
    void starterAnimalIsMouseAndStarterIdsAreValidated() {
        AnimalDefinition starter = AnimalDefinition.starter();

        assertNotNull(starter);
        assertEquals("mouse", starter.id());
        assertEquals(1, starter.tier());
        assertEquals(0, starter.xpRequired());
        assertTrue(AnimalDefinition.isValidStarter(null));
        assertTrue(AnimalDefinition.isValidStarter("mouse"));
        assertTrue(AnimalDefinition.isValidStarter("shrimp"));
        assertTrue(AnimalDefinition.isValidStarter("chipmunk"));
        assertFalse(AnimalDefinition.isValidStarter("lemming"));
        assertFalse(AnimalDefinition.isValidStarter("dragon"));
    }

    @Test
    void byIdReturnsCorrectAnimal() {
        AnimalDefinition fox = AnimalDefinition.byId("fox");
        assertNotNull(fox);
        assertEquals("Fox", fox.name());
        assertEquals(6, fox.tier());
        assertEquals("dash", fox.abilityId());
    }

    @Test
    void byIdReturnsNullForUnknown() {
        assertNull(AnimalDefinition.byId("unicorn"));
    }

    @Test
    void registryCoversExpectedTierCurve() {
        Set<Integer> tiers = AnimalDefinition.all().values().stream()
                .map(AnimalDefinition::tier)
                .collect(Collectors.toSet());

        assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 17), tiers);
    }

    @Test
    void xpRequiredIsConsistentWithinTierAndNonDecreasingAcrossTiers() {
        int previousTier = 0;
        int previousXp = -1;
        for (AnimalDefinition animal : AnimalDefinition.all().values()) {
            if (animal.tier() != previousTier) {
                assertTrue(animal.xpRequired() > previousXp,
                        animal.id() + " xpRequired should increase at each new tier");
                previousTier = animal.tier();
                previousXp = animal.xpRequired();
            } else {
                assertEquals(previousXp, animal.xpRequired(),
                        animal.id() + " xpRequired should match its tier");
            }
        }
    }

    @Test
    void tierPredationAllowsHigherTierWithinSixTierWindow() {
        AnimalDefinition lion = AnimalDefinition.byId("lion");

        assertTrue(lion.canEat("deer"));
        assertTrue(lion.canEat("fox"));
        assertFalse(lion.canEat("pig"));
        assertFalse(lion.canEat("croc"));
        assertFalse(AnimalDefinition.byId("mouse").canEat("rabbit"));
    }

    @Test
    void evolutionOptionsReturnAllAnimalsAtNextAvailableTier() {
        List<String> mouseOptions = AnimalDefinition.byId("mouse").evolutionOptions().stream()
                .map(AnimalDefinition::id)
                .toList();
        List<String> hippoOptions = AnimalDefinition.byId("hippo").evolutionOptions().stream()
                .map(AnimalDefinition::id)
                .toList();
        List<String> mammothOptions = AnimalDefinition.byId("mammoth").evolutionOptions().stream()
                .map(AnimalDefinition::id)
                .toList();

        assertEquals(List.of("rabbit", "arctichare", "trout"), mouseOptions);
        assertEquals(List.of("mammoth"), hippoOptions);
        assertEquals(List.of("dragon", "kraken", "yeti"), mammothOptions);
    }

    @Test
    void blackdragonIsFinalUnlockOnly() {
        AnimalDefinition dragon = AnimalDefinition.byId("dragon");
        AnimalDefinition blackdragon = AnimalDefinition.byId("blackdragon");

        assertTrue(dragon.evolutionOptions().isEmpty());
        assertFalse(dragon.canUnlockFinal(2_999_999));
        assertTrue(dragon.canUnlockFinal(3_000_000));
        assertFalse(blackdragon.normalEvolution());
    }

    @Test
    void allAnimalsHavePositiveStatsAndKnownAbility() {
        Set<String> abilityIds = Set.of(
                "dash", "burrow_dash", "dig_dash", "shell_guard", "ice_slide", "stink_dash",
                "forage_dash", "ink_dash", "sting_pulse", "back_kick", "charge", "shock_pulse",
                "claw", "inflate_guard", "roar_pulse", "croc_bite", "wave_pulse",
                "snowball_dash", "fire_dash", "whirlpool_pulse", "freeze_pulse"
        );

        for (AnimalDefinition animal : AnimalDefinition.all().values()) {
            assertTrue(animal.speed() > 0, animal.id() + " speed should be positive");
            assertTrue(animal.radius() > 0, animal.id() + " radius should be positive");
            assertTrue(animal.maxHealth() > 0, animal.id() + " maxHealth should be positive");
            assertTrue(abilityIds.contains(animal.abilityId()), animal.id() + " has unknown ability");
        }
    }

    @Test
    void registryIncludesAllGameplayBiomes() {
        assertEquals(Biome.LAND, AnimalDefinition.byId("mouse").biome());
        assertEquals(Biome.OCEAN, AnimalDefinition.byId("shrimp").biome());
        assertEquals(Biome.ARCTIC, AnimalDefinition.byId("chipmunk").biome());
        assertEquals(Biome.FINAL, AnimalDefinition.byId("blackdragon").biome());
    }

    @Test
    void idsMatchMapKeys() {
        for (Map.Entry<String, AnimalDefinition> entry : AnimalDefinition.all().entrySet()) {
            assertEquals(entry.getKey(), entry.getValue().id(),
                    "Map key should match animal id");
        }
    }
}
