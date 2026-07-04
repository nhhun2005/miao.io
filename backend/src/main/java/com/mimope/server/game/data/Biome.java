package com.mimope.server.game.data;

/**
 * Biome types available in the Mimope game world.
 * <p>
 * Each animal and food definition belongs to a specific biome.
 */
public enum Biome {

    /** Default grassland / forest biome. */
    LAND,

    /** Ocean / water biome. */
    OCEAN,

    /** Arctic / ice biome. */
    ARCTIC,

    /** Final unlock tier that is not tied to one terrain biome. */
    FINAL;

    /**
     * Return the lowercase string representation used in protocol messages.
     * Must match the frontend biome string values exactly.
     */
    public String toProtocol() {
        return name().toLowerCase();
    }

    /**
     * Parse a biome from a protocol string (case-insensitive).
     *
     * @param value the string to parse
     * @return the matching Biome
     * @throws IllegalArgumentException if the value is not a valid biome
     */
    public static Biome fromProtocol(String value) {
        return valueOf(value.toUpperCase());
    }
}
