package com.mimope.server.websocket;

import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a connected client session.
 * <p>
 * Wraps the raw {@link WebSocketSession} and stores game-relevant metadata
 * such as the validated nickname and last-seen timestamp (for ping tracking).
 */
public class ClientSession {

    private final String id;
    private final WebSocketSession webSocketSession;
    private String nickname;
    private Instant connectedAt;
    private Instant lastPongAt;

    public ClientSession(WebSocketSession webSocketSession) {
        this.id = webSocketSession.getId();
        this.webSocketSession = Objects.requireNonNull(webSocketSession);
        this.connectedAt = Instant.now();
        this.lastPongAt = this.connectedAt;
    }

    public String getId() {
        return id;
    }

    public WebSocketSession getWebSocketSession() {
        return webSocketSession;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public Instant getLastPongAt() {
        return lastPongAt;
    }

    public void setLastPongAt(Instant lastPongAt) {
        this.lastPongAt = lastPongAt;
    }

    public boolean isOpen() {
        return webSocketSession.isOpen();
    }

    @Override
    public String toString() {
        return "ClientSession{id='" + id + "', nickname='" + nickname + "'}";
    }
}
