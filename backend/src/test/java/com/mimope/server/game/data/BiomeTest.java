package com.mimope.server.game.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Biome} enum.
 */
class BiomeTest {

    @Test
    void toProtocolReturnsLowercase() {
        assertEquals("land", Biome.LAND.toProtocol());
        assertEquals("ocean", Biome.OCEAN.toProtocol());
        assertEquals("arctic", Biome.ARCTIC.toProtocol());
    }

    @Test
    void fromProtocolParsesCorrectly() {
        assertEquals(Biome.LAND, Biome.fromProtocol("land"));
        assertEquals(Biome.OCEAN, Biome.fromProtocol("OCEAN"));
        assertEquals(Biome.ARCTIC, Biome.fromProtocol("Arctic"));
    }

    @Test
    void fromProtocolThrowsForInvalid() {
        assertThrows(IllegalArgumentException.class, () -> Biome.fromProtocol("swamp"));
    }
}
