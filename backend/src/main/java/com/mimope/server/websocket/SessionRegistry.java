package com.mimope.server.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of all connected {@link ClientSession}s.
 * <p>
 * Sessions are indexed by their WebSocket session ID.
 */
@Component
public class SessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);

    private final ConcurrentHashMap<String, ClientSession> sessions = new ConcurrentHashMap<>();

    /**
     * Register a new session. Replaces any previous session with the same ID.
     */
    public void add(ClientSession session) {
        sessions.put(session.getId(), session);
        log.info("Session registered: {} (total: {})", session.getId(), sessions.size());
    }

    /**
     * Remove a session by ID.
     *
     * @return the removed session, or {@code null} if not found
     */
    public ClientSession remove(String sessionId) {
        ClientSession removed = sessions.remove(sessionId);
        if (removed != null) {
            log.info("Session removed: {} (total: {})", sessionId, sessions.size());
        }
        return removed;
    }

    /**
     * Look up a session by ID.
     */
    public ClientSession get(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * Return an unmodifiable view of all active sessions.
     */
    public Collection<ClientSession> getAll() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    /**
     * Current number of connected sessions.
     */
    public int size() {
        return sessions.size();
    }
}
