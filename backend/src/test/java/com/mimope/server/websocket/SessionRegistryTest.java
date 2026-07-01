package com.mimope.server.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionRegistryTest {

    private SessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SessionRegistry();
    }

    private ClientSession mockSession(String id) {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getId()).thenReturn(id);
        when(ws.isOpen()).thenReturn(true);
        return new ClientSession(ws);
    }

    @Test
    void addAndGet() {
        ClientSession session = mockSession("s1");
        registry.add(session);

        assertSame(session, registry.get("s1"));
        assertEquals(1, registry.size());
    }

    @Test
    void removeExisting() {
        ClientSession session = mockSession("s1");
        registry.add(session);

        ClientSession removed = registry.remove("s1");
        assertSame(session, removed);
        assertNull(registry.get("s1"));
        assertEquals(0, registry.size());
    }

    @Test
    void removeNonExistentReturnsNull() {
        assertNull(registry.remove("nonexistent"));
    }

    @Test
    void getAllReturnsAllSessions() {
        registry.add(mockSession("s1"));
        registry.add(mockSession("s2"));
        registry.add(mockSession("s3"));

        assertEquals(3, registry.getAll().size());
    }

    @Test
    void addReplacesExistingWithSameId() {
        ClientSession first = mockSession("s1");
        ClientSession second = mockSession("s1");

        registry.add(first);
        registry.add(second);

        assertSame(second, registry.get("s1"));
        assertEquals(1, registry.size());
    }

    @Test
    void sizeReflectsAdditionsAndRemovals() {
        assertEquals(0, registry.size());

        registry.add(mockSession("s1"));
        assertEquals(1, registry.size());

        registry.add(mockSession("s2"));
        assertEquals(2, registry.size());

        registry.remove("s1");
        assertEquals(1, registry.size());
    }
}
