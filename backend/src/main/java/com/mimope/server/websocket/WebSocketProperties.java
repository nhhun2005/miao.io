package com.mimope.server.websocket;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Environment-specific browser origins allowed to upgrade the game socket. */
@ConfigurationProperties("game.websocket")
public record WebSocketProperties(List<String> allowedOriginPatterns) {
    public WebSocketProperties {
        allowedOriginPatterns = allowedOriginPatterns == null
                ? List.of()
                : List.copyOf(allowedOriginPatterns);
    }
}
