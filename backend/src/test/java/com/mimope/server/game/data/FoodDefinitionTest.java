package com.mimope.server.game.data;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link FoodDefinition} game data.
 */
class FoodDefinitionTest {

    @Test
    void registryContainsAllFirstPlayableFoods() {
        Map<String, FoodDefinition> all = FoodDefinition.all();
        assertEquals(9, all.size());
        assertNotNull(all.get("berry"));
        assertNotNull(all.get("banana"));
        assertNotNull(all.get("meat"));
        assertNotNull(all.get("coconut"));
        assertNotNull(all.get("watermelon"));
        assertNotNull(all.get("seaweed"));
        assertNotNull(all.get("arctic_berry"));
        assertNotNull(all.get("snail"));
        assertNotNull(all.get("snail2"));
    }

    @Test
    void byIdReturnsCorrectFood() {
        FoodDefinition berry = FoodDefinition.byId("berry");
        assertNotNull(berry);
        assertEquals("Berry", berry.name());
        assertEquals(5, berry.xp());
    }

    @Test
    void byIdReturnsNullForUnknown() {
        assertNull(FoodDefinition.byId("pizza"));
    }

    @Test
    void allIdsReturnsCorrectList() {
        List<String> ids = FoodDefinition.allIds();
        assertEquals(9, ids.size());
        assertTrue(ids.contains("berry"));
        assertTrue(ids.contains("banana"));
        assertTrue(ids.contains("meat"));
        assertTrue(ids.contains("coconut"));
        assertTrue(ids.contains("watermelon"));
        assertTrue(ids.contains("seaweed"));
        assertTrue(ids.contains("arctic_berry"));
        assertTrue(ids.contains("snail"));
        assertTrue(ids.contains("snail2"));
    }

    @Test
    void totalSpawnWeightIsCorrect() {
        // 50 + 30 + 10 + 20 + 5 + 35 + 35 + 8 + 0 = 193
        assertEquals(193, FoodDefinition.totalSpawnWeight());
    }

    @Test
    void canBeEatenByTierWorksCorrectly() {
        FoodDefinition berry = FoodDefinition.byId("berry");
        assertNotNull(berry);
        assertTrue(berry.canBeEatenByTier(1));
        assertTrue(berry.canBeEatenByTier(6));

        FoodDefinition meat = FoodDefinition.byId("meat");
        assertNotNull(meat);
        assertFalse(meat.canBeEatenByTier(1));
        assertFalse(meat.canBeEatenByTier(2));
        assertTrue(meat.canBeEatenByTier(3));
        assertTrue(meat.canBeEatenByTier(6));

        FoodDefinition watermelon = FoodDefinition.byId("watermelon");
        assertNotNull(watermelon);
        assertFalse(watermelon.canBeEatenByTier(3));
        assertTrue(watermelon.canBeEatenByTier(4));
    }

    @Test
    void registryIncludesBiomeSpecificFoods() {
        assertEquals(Biome.LAND, FoodDefinition.byId("berry").biome());
        assertEquals(Biome.OCEAN, FoodDefinition.byId("seaweed").biome());
        assertEquals(Biome.ARCTIC, FoodDefinition.byId("arctic_berry").biome());
    }

    @Test
    void allFoodsHavePositiveStats() {
        for (FoodDefinition food : FoodDefinition.all().values()) {
            assertTrue(food.xp() > 0, food.id() + " xp should be positive");
            assertTrue(food.radius() > 0, food.id() + " radius should be positive");
            assertTrue(food.spawnWeight() >= 0, food.id() + " spawnWeight should be non-negative");
            assertTrue(food.minTier() >= 1, food.id() + " minTier should be >= 1");
        }
    }

    @Test
    void idsMatchMapKeys() {
        for (Map.Entry<String, FoodDefinition> entry : FoodDefinition.all().entrySet()) {
            assertEquals(entry.getKey(), entry.getValue().id(),
                    "Map key should match food id");
        }
    }
}
