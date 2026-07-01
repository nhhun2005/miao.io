package com.mimope.server.protocol.inbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimope.server.websocket.InboundMessage;
import com.mimope.server.websocket.MessageDecoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the typed inbound (client → server) protocol DTOs.
 * Each DTO's {@code from(InboundMessage)} factory is exercised with
 * valid and invalid payloads.
 */
class InboundMessageParsingTest {

    private MessageDecoder decoder;

    @BeforeEach
    void setUp() {
        decoder = new MessageDecoder(new ObjectMapper());
    }

    private InboundMessage decode(String json) {
        return decoder.decode(json).orElseThrow(() ->
                new AssertionError("Failed to decode: " + json));
    }

    // -----------------------------------------------------------------------
    // JoinMessage
    // -----------------------------------------------------------------------

    @Nested
    class JoinMessageTests {

        @Test
        void parsesValidJoin() {
            InboundMessage raw = decode("""
                    {"type":"join","nickname":"Alice"}
                    """);
            JoinMessage join = JoinMessage.from(raw);
            assertNotNull(join);
            assertEquals("Alice", join.nickname());
        }

        @Test
        void trimsNickname() {
            InboundMessage raw = decode("""
                    {"type":"join","nickname":"  Bob  "}
                    """);
            JoinMessage join = JoinMessage.from(raw);
            assertNotNull(join);
            assertEquals("Bob", join.nickname());
        }

        @Test
        void returnsNullForMissingNickname() {
            InboundMessage raw = decode("""
                    {"type":"join"}
                    """);
            assertNull(JoinMessage.from(raw));
        }

        @Test
        void returnsNullForBlankNickname() {
            InboundMessage raw = decode("""
                    {"type":"join","nickname":"   "}
                    """);
            assertNull(JoinMessage.from(raw));
        }

        @Test
        void returnsNullForNumericNickname() {
            InboundMessage raw = decode("""
                    {"type":"join","nickname":12345}
                    """);
            // nickname is a number, getString returns null
            assertNull(JoinMessage.from(raw));
        }
    }

    // -----------------------------------------------------------------------
    // InputMessage
    // -----------------------------------------------------------------------

    @Nested
    class InputMessageTests {

        @Test
        void parsesFullInput() {
            InboundMessage raw = decode("""
                    {"type":"input","seq":42,"angle":1.5708,"intensity":0.85,"boost":true,"ability":false,"timestamp":1719000000000}
                    """);
            InputMessage input = InputMessage.from(raw);
            assertNotNull(input);
            assertEquals(42, input.seq());
            assertEquals(1.5708, input.angle(), 0.0001);
            assertEquals(0.85, input.intensity(), 0.0001);
            assertTrue(input.boost());
            assertFalse(input.ability());
            assertEquals(1719000000000L, input.timestamp());
        }

        @Test
        void defaultsOptionalFields() {
            InboundMessage raw = decode("""
                    {"type":"input","seq":1,"angle":0.0}
                    """);
            InputMessage input = InputMessage.from(raw);
            assertNotNull(input);
            assertEquals(1, input.seq());
            assertEquals(0.0, input.angle(), 0.0001);
            assertEquals(1.0, input.intensity(), 0.0001); // default
            assertFalse(input.boost()); // default
            assertFalse(input.ability()); // default
            assertEquals(0L, input.timestamp()); // default
        }

        @Test
        void returnsNullForMissingSeq() {
            InboundMessage raw = decode("""
                    {"type":"input","angle":1.0}
                    """);
            assertNull(InputMessage.from(raw));
        }

        @Test
        void returnsNullForMissingAngle() {
            InboundMessage raw = decode("""
                    {"type":"input","seq":1}
                    """);
            assertNull(InputMessage.from(raw));
        }
    }

    // -----------------------------------------------------------------------
    // EvolveMessage
    // -----------------------------------------------------------------------

    @Nested
    class EvolveMessageTests {

        @Test
        void parsesValidEvolve() {
            InboundMessage raw = decode("""
                    {"type":"evolve","animalId":"rabbit"}
                    """);
            EvolveMessage evolve = EvolveMessage.from(raw);
            assertNotNull(evolve);
            assertEquals("rabbit", evolve.animalId());
        }

        @Test
        void trimsAnimalId() {
            InboundMessage raw = decode("""
                    {"type":"evolve","animalId":" fox "}
                    """);
            EvolveMessage evolve = EvolveMessage.from(raw);
            assertNotNull(evolve);
            assertEquals("fox", evolve.animalId());
        }

        @Test
        void returnsNullForMissingAnimalId() {
            InboundMessage raw = decode("""
                    {"type":"evolve"}
                    """);
            assertNull(EvolveMessage.from(raw));
        }

        @Test
        void returnsNullForBlankAnimalId() {
            InboundMessage raw = decode("""
                    {"type":"evolve","animalId":"  "}
                    """);
            assertNull(EvolveMessage.from(raw));
        }
    }

    // -----------------------------------------------------------------------
    // PingMessage
    // -----------------------------------------------------------------------

    @Nested
    class PingMessageTests {

        @Test
        void parsesWithTimestamp() {
            InboundMessage raw = decode("""
                    {"type":"ping","timestamp":1234567890}
                    """);
            PingMessage ping = PingMessage.from(raw);
            assertNotNull(ping);
            assertEquals(1234567890L, ping.timestamp());
        }

        @Test
        void defaultsTimestampToZero() {
            InboundMessage raw = decode("""
                    {"type":"ping"}
                    """);
            PingMessage ping = PingMessage.from(raw);
            assertNotNull(ping);
            assertEquals(0L, ping.timestamp());
        }
    }
}
