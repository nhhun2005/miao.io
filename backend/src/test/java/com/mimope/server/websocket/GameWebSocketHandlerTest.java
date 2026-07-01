package com.mimope.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimope.server.game.GameRoom;
import com.mimope.server.game.SnapshotMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameWebSocketHandlerTest {

    private SessionRegistry sessionRegistry;
    private MessageDecoder messageDecoder;
    private MessageEncoder messageEncoder;
    private NicknameValidator nicknameValidator;
    private GameRoom gameRoom;
    private GameWebSocketHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        sessionRegistry = new SessionRegistry();
        messageDecoder = new MessageDecoder(objectMapper);
        messageEncoder = new MessageEncoder(objectMapper);
        nicknameValidator = new NicknameValidator();
        gameRoom = new GameRoom(sessionRegistry, messageEncoder, new SnapshotMetrics(), 5000, 5000, 50, 200, 20);
        gameRoom.init(); // initialize world and loop
        handler = new GameWebSocketHandler(sessionRegistry, messageDecoder, messageEncoder, nicknameValidator, gameRoom);
    }

    @AfterEach
    void tearDown() {
        gameRoom.shutdown(); // stop the game loop
    }

    private WebSocketSession mockWsSession(String id) {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getId()).thenReturn(id);
        when(ws.isOpen()).thenReturn(true);
        when(ws.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        return ws;
    }

    @Test
    void connectionEstablishedRegistersSession() throws Exception {
        WebSocketSession ws = mockWsSession("s1");
        handler.afterConnectionEstablished(ws);

        assertNotNull(sessionRegistry.get("s1"));
        assertEquals(1, sessionRegistry.size());
    }

    @Test
    void connectionClosedRemovesSession() throws Exception {
        WebSocketSession ws = mockWsSession("s1");
        handler.afterConnectionEstablished(ws);
        handler.afterConnectionClosed(ws, CloseStatus.NORMAL);

        assertNull(sessionRegistry.get("s1"));
        assertEquals(0, sessionRegistry.size());
    }

    @Test
    void transportErrorRemovesSession() throws Exception {
        WebSocketSession ws = mockWsSession("s1");
        handler.afterConnectionEstablished(ws);
        handler.handleTransportError(ws, new RuntimeException("test error"));

        assertNull(sessionRegistry.get("s1"));
    }

    @Test
    void joinWithValidNicknameSendsWelcome() throws Exception {
        WebSocketSession ws = mockWsSession("s1");
        handler.afterConnectionEstablished(ws);

        TextMessage msg = new TextMessage("""
                {"type":"join","nickname":"TestPlayer"}
                """);
        handler.handleMessage(ws, msg);

        ClientSession session = sessionRegistry.get("s1");
        assertEquals("TestPlayer", session.getNickname());

        // Verify a welcome message was sent
        verify(ws, atLeastOnce()).sendMessage(argThat(m -> {
            String payload = ((TextMessage) m).getPayload();
            return payload.contains("\"type\":\"welcome\"") && payload.contains("\"nickname\":\"TestPlayer\"");
        }));
    }

    @Test
    void joinWithInvalidNicknameSendsError() throws Exception {
        WebSocketSession ws = mockWsSession("s1");
        handler.afterConnectionEstablished(ws);

        TextMessage msg = new TextMessage("""
                {"type":"join","nickname":"<script>alert(1)</script>"}
                """);
        handler.handleMessage(ws, msg);

        ClientSession session = sessionRegistry.get("s1");
        assertNull(session.getNickname());

        // Verify an error message was sent
        verify(ws, atLeastOnce()).sendMessage(argThat(m -> {
            String payload = ((TextMessage) m).getPayload();
            return payload.contains("\"type\":\"error\"");
        }));
    }

    @Test
    void pingSendsPong() throws Exception {
        WebSocketSession ws = mockWsSession("s1");
        handler.afterConnectionEstablished(ws);

        TextMessage msg = new TextMessage("""
                {"type":"ping","timestamp":999}
                """);
        handler.handleMessage(ws, msg);

        verify(ws, atLeastOnce()).sendMessage(argThat(m -> {
            String payload = ((TextMessage) m).getPayload();
            return payload.contains("\"type\":\"pong\"") && payload.contains("999");
        }));
    }

    @Test
    void pingWithoutTimestampStillSendsPong() throws Exception {
        WebSocketSession ws = mockWsSession("s1");
        handler.afterConnectionEstablished(ws);

        TextMessage msg = new TextMessage("""
                {"type":"ping"}
                """);
        handler.handleMessage(ws, msg);

        verify(ws, atLeastOnce()).sendMessage(argThat(m -> {
            String payload = ((TextMessage) m).getPayload();
            return payload.contains("\"type\":\"pong\"");
        }));
    }

    @Test
    void gridDebugToggleUpdatesRoomSetting() throws Exception {
        WebSocketSession ws = mockWsSession("s1");
        handler.afterConnectionEstablished(ws);

        handler.handleMessage(ws, new TextMessage("""
                {"type":"grid_debug","enabled":true}
                """));

        assertTrue(gameRoom.isGridDebugEnabled());

        handler.handleMessage(ws, new TextMessage("""
                {"type":"grid_debug","enabled":false}
                """));

        assertFalse(gameRoom.isGridDebugEnabled());
    }

    @Test
    void evolveWithValidOptionUpdatesPlayerAnimal() throws Exception {
        WebSocketSession ws = mockWsSession("s1");
        handler.afterConnectionEstablished(ws);

        handler.handleMessage(ws, new TextMessage("""
                {"type":"join","nickname":"TestPlayer"}
                """));
        gameRoom.getWorld().getPlayer("s1").addXp(50);

        handler.handleMessage(ws, new TextMessage("""
                {"type":"evolve","animalId":"rabbit"}
                """));

        assertEquals("rabbit", gameRoom.getWorld().getPlayer("s1").getAnimal().id());
    }

    @Test
    void evolveWithInvalidOptionSendsError() throws Exception {
        WebSocketSession ws = mockWsSession("s1");
        handler.afterConnectionEstablished(ws);

        handler.handleMessage(ws, new TextMessage("""
                {"type":"join","nickname":"TestPlayer"}
                """));
        handler.handleMessage(ws, new TextMessage("""
                {"type":"evolve","animalId":"rabbit"}
                """));

        verify(ws, atLeastOnce()).sendMessage(argThat(m -> {
            String payload = ((TextMessage) m).getPayload();
            return payload.contains("\"type\":\"error\"")
                    && payload.contains("Evolution is not available yet");
        }));
    }

    @Test
    void unknownTypeSendsError() throws Exception {
        WebSocketSession ws = mockWsSession("s1");
        handler.afterConnectionEstablished(ws);

        TextMessage msg = new TextMessage("""
                {"type":"unknown_cmd"}
                """);
        handler.handleMessage(ws, msg);

        verify(ws, atLeastOnce()).sendMessage(argThat(m -> {
            String payload = ((TextMessage) m).getPayload();
            return payload.contains("\"type\":\"error\"") && payload.contains("Unknown message type");
        }));
    }

    @Test
    void malformedJsonSendsError() throws Exception {
        WebSocketSession ws = mockWsSession("s1");
        handler.afterConnectionEstablished(ws);

        TextMessage msg = new TextMessage("not json");
        handler.handleMessage(ws, msg);

        verify(ws, atLeastOnce()).sendMessage(argThat(m -> {
            String payload = ((TextMessage) m).getPayload();
            return payload.contains("\"type\":\"error\"") && payload.contains("Malformed");
        }));
    }
}
