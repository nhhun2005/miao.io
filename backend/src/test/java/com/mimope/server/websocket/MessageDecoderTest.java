package com.mimope.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MessageDecoderTest {

    private MessageDecoder decoder;

    @BeforeEach
    void setUp() {
        decoder = new MessageDecoder(new ObjectMapper());
    }

    @Test
    void decodesValidJoinMessage() {
        String json = """
                {"type":"join","nickname":"TestPlayer"}
                """;
        Optional<InboundMessage> result = decoder.decode(json);

        assertTrue(result.isPresent());
        InboundMessage msg = result.get();
        assertEquals("join", msg.type());
        assertEquals("TestPlayer", msg.getString("nickname"));
    }

    @Test
    void decodesPingWithTimestamp() {
        String json = """
                {"type":"ping","timestamp":1234567890}
                """;
        Optional<InboundMessage> result = decoder.decode(json);

        assertTrue(result.isPresent());
        InboundMessage msg = result.get();
        assertEquals("ping", msg.type());
        assertEquals(1234567890, msg.getNumber("timestamp").intValue());
    }

    @Test
    void decodesMessageWithExtraFields() {
        String json = """
                {"type":"input","angle":1.5,"boost":true,"seq":42}
                """;
        Optional<InboundMessage> result = decoder.decode(json);

        assertTrue(result.isPresent());
        InboundMessage msg = result.get();
        assertEquals("input", msg.type());
        assertEquals(1.5, msg.getNumber("angle").doubleValue(), 0.001);
        assertEquals(42, msg.getNumber("seq").intValue());
        assertFalse(msg.payload().containsKey("type"));
    }

    @Test
    void returnsEmptyForNull() {
        assertTrue(decoder.decode(null).isEmpty());
    }

    @Test
    void returnsEmptyForEmptyString() {
        assertTrue(decoder.decode("").isEmpty());
    }

    @Test
    void returnsEmptyForBlankString() {
        assertTrue(decoder.decode("   ").isEmpty());
    }

    @Test
    void returnsEmptyForInvalidJson() {
        assertTrue(decoder.decode("not json at all").isEmpty());
    }

    @Test
    void returnsEmptyForJsonWithoutType() {
        assertTrue(decoder.decode("""
                {"nickname":"NoType"}
                """).isEmpty());
    }

    @Test
    void returnsEmptyForNonStringType() {
        assertTrue(decoder.decode("""
                {"type":123}
                """).isEmpty());
    }

    @Test
    void returnsEmptyForBlankType() {
        assertTrue(decoder.decode("""
                {"type":"  "}
                """).isEmpty());
    }

    @Test
    void getStringReturnsNullForMissingKey() {
        String json = """
                {"type":"test"}
                """;
        InboundMessage msg = decoder.decode(json).orElseThrow();
        assertNull(msg.getString("missing"));
    }

    @Test
    void getStringReturnsNullForNonStringValue() {
        String json = """
                {"type":"test","count":5}
                """;
        InboundMessage msg = decoder.decode(json).orElseThrow();
        assertNull(msg.getString("count"));
    }

    @Test
    void getNumberReturnsNullForMissingKey() {
        String json = """
                {"type":"test"}
                """;
        InboundMessage msg = decoder.decode(json).orElseThrow();
        assertNull(msg.getNumber("missing"));
    }

    @Test
    void getNumberReturnsNullForNonNumericValue() {
        String json = """
                {"type":"test","name":"hello"}
                """;
        InboundMessage msg = decoder.decode(json).orElseThrow();
        assertNull(msg.getNumber("name"));
    }
}
