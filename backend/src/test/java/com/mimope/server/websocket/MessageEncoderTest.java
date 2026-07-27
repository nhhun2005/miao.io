package com.mimope.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageEncoderTest {

    @Test
    void closedSessionRaceDoesNotEscapeIntoGameLoop() throws Exception {
        WebSocketSession webSocketSession = mock(WebSocketSession.class);
        when(webSocketSession.getId()).thenReturn("closing-session");
        when(webSocketSession.isOpen()).thenReturn(true);
        when(webSocketSession.getRemoteAddress()).thenReturn(null);
        org.mockito.Mockito.doThrow(new IllegalStateException("session closed during send"))
                .when(webSocketSession).sendMessage(any());

        ClientSession session = new ClientSession(webSocketSession);
        MessageEncoder encoder = new MessageEncoder(new ObjectMapper());

        assertDoesNotThrow(() -> encoder.send(session, "snapshot", Map.of("tick", 1L)));
    }
}
