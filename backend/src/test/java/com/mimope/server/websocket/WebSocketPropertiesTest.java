package com.mimope.server.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WebSocketPropertiesTest {
    @Test
    void bindsCommaSeparatedLocalAndDeploymentPatterns() {
        var source = new MapConfigurationPropertySource(Map.of(
                "game.websocket.allowed-origin-patterns",
                "http://localhost:*,http://127.0.0.1:*,https://game.example.com"));
        WebSocketProperties properties = new Binder(source)
                .bind("game.websocket", WebSocketProperties.class)
                .orElseThrow(() -> new AssertionError("properties did not bind"));
        assertEquals(3, properties.allowedOriginPatterns().size());
        assertTrue(properties.allowedOriginPatterns().contains("http://localhost:*"));
        assertTrue(properties.allowedOriginPatterns().contains("http://127.0.0.1:*"));
        assertTrue(properties.allowedOriginPatterns().contains("https://game.example.com"));
        assertFalse(properties.allowedOriginPatterns().contains("*"));
    }

    @Test
    void propertiesAreImmutable() {
        WebSocketProperties properties = new WebSocketProperties(
                java.util.List.of("http://localhost:*"));
        assertThrows(UnsupportedOperationException.class,
                () -> properties.allowedOriginPatterns().add("*"));
    }
}
