package com.mimope.server.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end WebSocket integration tests that exercise the real
 * {@code /ws/game} endpoint over a running server on a random port.
 *
 * <p>Covers the previously-manual checklist items:
 * <ul>
 *   <li>Two or more players joining the same room and seeing each other.</li>
 *   <li>A player "refreshing" (dropping the socket and rejoining with a new
 *       session) without leaving a stale player entity behind.</li>
 *   <li>Join flow producing a welcome and subsequent snapshots.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GameWebSocketIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private com.mimope.server.game.GameRoom gameRoom;

    /** A test client that records every inbound message. */
    private static final class RecordingClient extends TextWebSocketHandler {
        final List<JsonNode> messages = new CopyOnWriteArrayList<>();
        volatile WebSocketSession session;

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            this.session = session;
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            messages.add(MAPPER.readTree(message.getPayload()));
        }

        void send(String json) throws Exception {
            session.sendMessage(new TextMessage(json));
        }

        long count(String type) {
            return messages.stream().filter(m -> type.equals(m.path("type").asText())).count();
        }

        JsonNode firstOf(String type) {
            return messages.stream()
                    .filter(m -> type.equals(m.path("type").asText()))
                    .findFirst()
                    .orElse(null);
        }
    }

    private RecordingClient connect() throws Exception {
        RecordingClient handler = new RecordingClient();
        StandardWebSocketClient client = new StandardWebSocketClient();
        URI uri = URI.create("ws://localhost:" + port + "/ws/game");
        client.execute(handler, new WebSocketHttpHeaders(), uri).get(20, TimeUnit.SECONDS);
        await().atMost(20, TimeUnit.SECONDS).until(() -> handler.session != null && handler.session.isOpen());
        return handler;
    }

    @Test
    void joinReceivesWelcomeAndSnapshots() throws Exception {
        RecordingClient client = connect();
        client.send("{\"type\":\"join\",\"nickname\":\"Solo\"}");

        await().atMost(20, TimeUnit.SECONDS).until(() -> client.count("welcome") == 1);
        await().atMost(20, TimeUnit.SECONDS).until(() -> client.count("snapshot") >= 1);

        JsonNode welcome = client.firstOf("welcome");
        assertNotNull(welcome);
        assertFalse(welcome.path("playerId").asText().isBlank());

        client.session.close();
    }

    @Test
    void twoPlayersJoinSameRoomAndSeeEachOther() throws Exception {
        RecordingClient a = connect();
        RecordingClient b = connect();

        a.send("{\"type\":\"join\",\"nickname\":\"Alice\"}");
        b.send("{\"type\":\"join\",\"nickname\":\"Bob\"}");

        await().atMost(20, TimeUnit.SECONDS).until(() -> a.count("welcome") == 1 && b.count("welcome") == 1);

        // Authoritative check: both players share the one room. Snapshot player
        // lists are viewport-filtered, so proximity is not guaranteed with
        // random spawns; the room membership is the reliable signal.
        await().atMost(20, TimeUnit.SECONDS).until(() -> {
            List<String> names = gameRoom.getWorld().getPlayers().stream()
                    .map(com.mimope.server.game.PlayerEntity::getNickname)
                    .toList();
            return names.contains("Alice") && names.contains("Bob");
        });

        // Both clients are actively receiving world snapshots.
        await().atMost(20, TimeUnit.SECONDS).until(() -> a.count("snapshot") >= 1 && b.count("snapshot") >= 1);

        a.session.close();
        b.session.close();
    }


    @Test
    void refreshDropsStaleSessionAndRejoinsCleanly() throws Exception {
        RecordingClient first = connect();
        first.send("{\"type\":\"join\",\"nickname\":\"Refresher\"}");
        await().atMost(20, TimeUnit.SECONDS).until(() -> first.count("welcome") == 1);
        String firstId = first.firstOf("welcome").path("playerId").asText();

        // Player is present after joining.
        await().atMost(20, TimeUnit.SECONDS).until(() -> playerIdPresent(firstId));

        // Simulate a browser refresh: the old socket closes and its entity is
        // cleaned up server-side.
        first.session.close(CloseStatus.NORMAL);
        await().atMost(20, TimeUnit.SECONDS).until(() -> !playerIdPresent(firstId));

        // ...and a brand-new socket joins with the same nickname, getting a new id.
        RecordingClient second = connect();
        second.send("{\"type\":\"join\",\"nickname\":\"Refresher\"}");
        await().atMost(20, TimeUnit.SECONDS).until(() -> second.count("welcome") == 1);
        String secondId = second.firstOf("welcome").path("playerId").asText();

        // The rejoined player exists and the stale one does not: no leak.
        assertNotEquals(firstId, secondId);
        await().atMost(20, TimeUnit.SECONDS).until(() -> playerIdPresent(secondId));
        assertFalse(playerIdPresent(firstId), "Refresh must not leave a stale player entity");

        second.session.close();
    }

    // ------------------------------------------------------------------ helpers

    private boolean playerIdPresent(String playerId) {
        return gameRoom.getWorld().getPlayer(playerId) != null;
    }
}




