package com.mimope.server.protocol;

/**
 * Shared protocol constants used by both encoder/decoder and handler layers.
 */
public final class ProtocolConstants {

    private ProtocolConstants() {}

    /**
     * Current protocol version. Incremented when breaking changes are made
     * to the message format. Both client and server must agree on this value.
     */
    public static final int PROTOCOL_VERSION = 1;

    // ------------------------------------------------------------------ Client → Server message types
    public static final String TYPE_JOIN = "join";
    public static final String TYPE_INPUT = "input";
    public static final String TYPE_EVOLVE = "evolve";
    public static final String TYPE_PING = "ping";
    public static final String TYPE_GRID_DEBUG = "grid_debug";
    public static final String TYPE_DEBUG_LEVEL_UP = "debug_levelup";

    // ------------------------------------------------------------------ Server → Client message types
    public static final String TYPE_WELCOME = "welcome";
    public static final String TYPE_SNAPSHOT = "snapshot";
    public static final String TYPE_EVOLUTION_OPTIONS = "evolution_options";
    public static final String TYPE_DEATH = "death";
    public static final String TYPE_PONG = "pong";
    public static final String TYPE_ERROR = "error";
}
