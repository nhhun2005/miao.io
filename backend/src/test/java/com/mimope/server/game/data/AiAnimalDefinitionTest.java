package com.mimope.server.game.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiAnimalDefinitionTest {

    @Test
    void registryContainsSnailFoodDefinitionsOutsideEvolutionTree() {
        AiAnimalDefinition snail = AiAnimalDefinition.all().get("snail");
        AiAnimalDefinition snail2 = AiAnimalDefinition.all().get("snail2");

        assertNotNull(snail);
        assertNotNull(snail2);
        assertEquals(8, snail.spawnWeight());
        assertNull(snail.variantOf());
        assertEquals("snail", snail2.variantOf());
        assertEquals(0.10, snail2.variantRollRate(), 0.0001);
        assertNull(AnimalDefinition.byId("snail"));
        assertNull(AnimalDefinition.byId("snail2"));
    }
}
