package com.mimope.server.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolConstantsTest {

    @Test
    void protocolVersionIsPositive() {
        assertTrue(ProtocolConstants.PROTOCOL_VERSION > 0);
    }

    @Test
    void clientMessageTypesAreNonBlank() {
        assertFalse(ProtocolConstants.TYPE_JOIN.isBlank());
        assertFalse(ProtocolConstants.TYPE_INPUT.isBlank());
        assertFalse(ProtocolConstants.TYPE_EVOLVE.isBlank());
        assertFalse(ProtocolConstants.TYPE_PING.isBlank());
        assertFalse(ProtocolConstants.TYPE_GRID_DEBUG.isBlank());
    }

    @Test
    void serverMessageTypesAreNonBlank() {
        assertFalse(ProtocolConstants.TYPE_WELCOME.isBlank());
        assertFalse(ProtocolConstants.TYPE_SNAPSHOT.isBlank());
        assertFalse(ProtocolConstants.TYPE_EVOLUTION_OPTIONS.isBlank());
        assertFalse(ProtocolConstants.TYPE_DEATH.isBlank());
        assertFalse(ProtocolConstants.TYPE_PONG.isBlank());
        assertFalse(ProtocolConstants.TYPE_ERROR.isBlank());
    }

    @Test
    void allTypesAreUnique() {
        String[] allTypes = {
                ProtocolConstants.TYPE_JOIN,
                ProtocolConstants.TYPE_INPUT,
                ProtocolConstants.TYPE_EVOLVE,
                ProtocolConstants.TYPE_PING,
                ProtocolConstants.TYPE_GRID_DEBUG,
                ProtocolConstants.TYPE_WELCOME,
                ProtocolConstants.TYPE_SNAPSHOT,
                ProtocolConstants.TYPE_EVOLUTION_OPTIONS,
                ProtocolConstants.TYPE_DEATH,
                ProtocolConstants.TYPE_PONG,
                ProtocolConstants.TYPE_ERROR,
        };
        assertEquals(allTypes.length, java.util.Set.of(allTypes).size(),
                "All message type strings must be unique");
    }
}
