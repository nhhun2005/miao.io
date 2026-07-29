package com.mimope.server.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the game WebSocket endpoint at {@code /ws/game}.
 * <p>
 * CORS is wide-open during development; tighten {@code setAllowedOrigins}
 * before any public deployment.
 */
@Configuration
@EnableWebSocket
@EnableConfigurationProperties(WebSocketProperties.class)
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler gameWebSocketHandler;
    private final WebSocketProperties properties;

    public WebSocketConfig(GameWebSocketHandler gameWebSocketHandler, WebSocketProperties properties) {
        this.gameWebSocketHandler = gameWebSocketHandler;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gameWebSocketHandler, "/ws/game")
                .setAllowedOriginPatterns(properties.allowedOriginPatterns().toArray(String[]::new));
    }
}
