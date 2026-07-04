package com.mimope.server.game.data;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnimalVariantDefinitionTest {

    @Test
    void registryContainsCosmeticVariantsOnly() {
        List<AnimalVariantDefinition> variants = AnimalVariantDefinition.all().values().stream().toList();

        assertEquals(5, variants.size());
        assertEquals("crab", AnimalVariantDefinition.all().get("crab2").baseAnimalId());
        assertEquals("turtle", AnimalVariantDefinition.all().get("turtle2").baseAnimalId());
        assertEquals("muskox", AnimalVariantDefinition.all().get("muskox2").baseAnimalId());
        assertEquals("pufferfish", AnimalVariantDefinition.all().get("pufferfish2").baseAnimalId());
        assertEquals("swordfish", AnimalVariantDefinition.all().get("swordfish2").baseAnimalId());

        for (AnimalVariantDefinition variant : variants) {
            assertEquals(0.10, variant.rollRate(), 0.0001);
            assertNotNull(AnimalDefinition.byId(variant.baseAnimalId()));
            assertNull(AnimalDefinition.byId(variant.id()));
        }
    }
}
