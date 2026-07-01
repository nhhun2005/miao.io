package com.mimope.server.game.data;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AnimalDefinition} game data.
 */
class AnimalDefinitionTest {

    @Test
    void registryContainsAllFirstPlayableAnimals() {
        Map<String, AnimalDefinition> all = AnimalDefinition.all();
        assertEquals(6, all.size());
        assertNotNull(all.get("mouse"));
        assertNotNull(all.get("rabbit"));
        assertNotNull(all.get("pig"));
        assertNotNull(all.get("fox"));
        assertNotNull(all.get("deer"));
        assertNotNull(all.get("lion"));
    }

    @Test
    void starterAnimalIsMouse() {
        AnimalDefinition starter = AnimalDefinition.starter();
        assertNotNull(starter);
        assertEquals("mouse", starter.id());
        assertEquals(1, starter.tier());
        assertEquals(0, starter.xpRequired());
    }

    @Test
    void byIdReturnsCorrectAnimal() {
        AnimalDefinition fox = AnimalDefinition.byId("fox");
        assertNotNull(fox);
        assertEquals("Fox", fox.name());
        assertEquals(4, fox.tier());
    }

    @Test
    void byIdReturnsNullForUnknown() {
        assertNull(AnimalDefinition.byId("unicorn"));
    }

    @Test
    void tiersAreSequential() {
        List<Integer> tiers = AnimalDefinition.all().values().stream()
                .map(AnimalDefinition::tier)
                .sorted()
                .toList();
        assertEquals(List.of(1, 2, 3, 4, 5, 6), tiers);
    }

    @Test
    void xpRequiredIncreasesWithTier() {
        int previousXp = -1;
        for (AnimalDefinition animal : AnimalDefinition.all().values()) {
            assertTrue(animal.xpRequired() > previousXp,
                    animal.id() + " xpRequired should be greater than previous tier");
            previousXp = animal.xpRequired();
        }
    }

    @Test
    void canEatReturnsTrueForValidPrey() {
        AnimalDefinition lion = AnimalDefinition.byId("lion");
        assertNotNull(lion);
        assertTrue(lion.canEat("deer"));
        assertTrue(lion.canEat("mouse"));
    }

    @Test
    void canEatReturnsFalseForInvalidPrey() {
        AnimalDefinition mouse = AnimalDefinition.byId("mouse");
        assertNotNull(mouse);
        assertFalse(mouse.canEat("lion"));
        assertFalse(mouse.canEat("rabbit"));
    }

    @Test
    void mouseCannotEatAnything() {
        AnimalDefinition mouse = AnimalDefinition.byId("mouse");
        assertNotNull(mouse);
        assertTrue(mouse.canEat().isEmpty());
    }

    @Test
    void evolutionOptionsReturnsNextTier() {
        AnimalDefinition pig = AnimalDefinition.byId("pig");
        assertNotNull(pig);
        List<AnimalDefinition> options = pig.evolutionOptions();
        assertEquals(1, options.size());
        assertEquals("fox", options.get(0).id());
    }

    @Test
    void lionHasNoEvolutionOptions() {
        AnimalDefinition lion = AnimalDefinition.byId("lion");
        assertNotNull(lion);
        assertTrue(lion.evolutionOptions().isEmpty());
    }

    @Test
    void allAnimalsHaveLandBiome() {
        for (AnimalDefinition animal : AnimalDefinition.all().values()) {
            assertEquals(Biome.LAND, animal.biome(),
                    animal.id() + " should be in LAND biome for Phase 2");
        }
    }

    @Test
    void allAnimalsHavePositiveStats() {
        for (AnimalDefinition animal : AnimalDefinition.all().values()) {
            assertTrue(animal.speed() > 0, animal.id() + " speed should be positive");
            assertTrue(animal.radius() > 0, animal.id() + " radius should be positive");
            assertTrue(animal.maxHealth() > 0, animal.id() + " maxHealth should be positive");
        }
    }

    @Test
    void idsMatchMapKeys() {
        for (Map.Entry<String, AnimalDefinition> entry : AnimalDefinition.all().entrySet()) {
            assertEquals(entry.getKey(), entry.getValue().id(),
                    "Map key should match animal id");
        }
    }
}
