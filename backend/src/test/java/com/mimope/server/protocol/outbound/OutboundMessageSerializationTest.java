package com.mimope.server.protocol.outbound;

import com.mimope.server.protocol.ProtocolConstants;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the outbound (server → client) protocol DTOs.
 * Verifies that each record's {@code toMap()} produces the expected fields
 * for JSON serialisation.
 */
class OutboundMessageSerializationTest {

    @Test
    void welcomeMessageFields() {
        WelcomeMessage msg = new WelcomeMessage("player-1", "Alice");
        Map<String, Object> map = msg.toMap();

        assertEquals("player-1", map.get("playerId"));
        assertEquals("Alice", map.get("nickname"));
        assertEquals(ProtocolConstants.PROTOCOL_VERSION, map.get("protocolVersion"));
    }

    @Test
    void welcomeMessageTypeConstant() {
        assertEquals("welcome", WelcomeMessage.TYPE);
    }

    @Test
    void pongMessageWithTimestamp() {
        PongMessage msg = new PongMessage(12345L);
        Map<String, Object> map = msg.toMap();

        assertEquals(12345L, map.get("timestamp"));
    }

    @Test
    void pongMessageWithoutTimestamp() {
        PongMessage msg = new PongMessage(0);
        Map<String, Object> map = msg.toMap();

        assertFalse(map.containsKey("timestamp"), "timestamp should be omitted when 0");
    }

    @Test
    void errorMessageFields() {
        ErrorMessage msg = new ErrorMessage("Something went wrong");
        Map<String, Object> map = msg.toMap();

        assertEquals("Something went wrong", map.get("message"));
        assertEquals("error", ErrorMessage.TYPE);
    }

    @Test
    void deathMessageFields() {
        DeathMessage msg = new DeathMessage("eaten", "ProPlayer", 450.0, 120000L);
        Map<String, Object> map = msg.toMap();

        assertEquals("eaten", map.get("reason"));
        assertEquals("ProPlayer", map.get("killerNickname"));
        assertEquals(450.0, map.get("xpEarned"));
        assertEquals(120000L, map.get("survivalTimeMs"));
    }

    @Test
    void deathMessageWithoutKiller() {
        DeathMessage msg = new DeathMessage("timeout", null, 0.0, 60000L);
        Map<String, Object> map = msg.toMap();

        assertEquals("timeout", map.get("reason"));
        assertFalse(map.containsKey("killerNickname"));
    }

    @Test
    void evolutionOptionsMessageFields() {
        EvolutionOptionsMessage msg = new EvolutionOptionsMessage(List.of(
                new EvolutionOptionsMessage.EvolutionOption("rabbit", "Rabbit", 2),
                new EvolutionOptionsMessage.EvolutionOption("pig", "Pig", 3)
        ));
        Map<String, Object> map = msg.toMap();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> options = (List<Map<String, Object>>) map.get("options");
        assertEquals(2, options.size());
        assertEquals("rabbit", options.get(0).get("animalId"));
        assertEquals("Rabbit", options.get(0).get("name"));
        assertEquals(2, options.get(0).get("tier"));
        assertEquals("pig", options.get(1).get("animalId"));
    }

    @Test
    void snapshotMessageFields() {
        SnapshotMessage msg = new SnapshotMessage(
                42L,
                List.of(new SnapshotMessage.PlayerData(
                        "p1", "Alice", 100.0, 200.0, 22.0, 1.5,
                        "mouse", "mouse", 100.0, 100.0, 0.0, 0.0, 0.0, 0L
                )),
                List.of(new SnapshotMessage.FoodData("f1", "berry", 300.0, 400.0)),
                List.of(new SnapshotMessage.LeaderboardEntry("Alice", 0.0)),
                List.of(new SnapshotMessage.FoodPickupData("f1", "berry", 300.0, 400.0, 5, "p1")),
                List.of(new SnapshotMessage.KillEventData("p2", "p1", "Alice", 100.0, 200.0, 75.0)),
                List.of(new SnapshotMessage.AbilityEventData("p1", "dash", 100.0, 200.0, 0.0)),
                null
        );
        Map<String, Object> map = msg.toMap();

        assertEquals(42L, map.get("tick"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> players = (List<Map<String, Object>>) map.get("players");
        assertEquals(1, players.size());
        assertEquals("p1", players.get(0).get("id"));
        assertEquals("Alice", players.get(0).get("nickname"));
        assertEquals(100.0, players.get(0).get("x"));
        assertEquals("mouse", players.get(0).get("animalId"));
        assertEquals("mouse", players.get(0).get("skinId"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> foods = (List<Map<String, Object>>) map.get("foods");
        assertEquals(1, foods.size());
        assertEquals("f1", foods.get(0).get("id"));
        assertEquals("berry", foods.get(0).get("foodId"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> leaderboard = (List<Map<String, Object>>) map.get("leaderboard");
        assertEquals(1, leaderboard.size());
        assertEquals("Alice", leaderboard.get(0).get("nickname"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> foodPickups = (List<Map<String, Object>>) map.get("foodPickups");
        assertEquals(1, foodPickups.size());
        assertEquals("f1", foodPickups.get(0).get("foodInstanceId"));
        assertEquals("p1", foodPickups.get(0).get("playerId"));
        assertEquals(5, foodPickups.get(0).get("xp"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> killEvents = (List<Map<String, Object>>) map.get("killEvents");
        assertEquals("p2", killEvents.get(0).get("victimId"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> abilityEvents = (List<Map<String, Object>>) map.get("abilityEvents");
        assertEquals("dash", abilityEvents.get(0).get("abilityId"));
    }

    @Test
    void snapshotPlayerDataToMap() {
        SnapshotMessage.PlayerData pd = new SnapshotMessage.PlayerData(
                "id1", "Bob", 50.0, 75.0, 28.0, 0.5, "rabbit", "rabbit",
                150.0, 150.0, 50.0, 0.0, 0.0, 20L
        );
        Map<String, Object> map = pd.toMap();

        assertEquals("id1", map.get("id"));
        assertEquals("Bob", map.get("nickname"));
        assertEquals(50.0, map.get("x"));
        assertEquals(75.0, map.get("y"));
        assertEquals(28.0, map.get("radius"));
        assertEquals(0.5, map.get("angle"));
        assertEquals("rabbit", map.get("animalId"));
        assertEquals("rabbit", map.get("skinId"));
        assertEquals(150.0, map.get("health"));
        assertEquals(150.0, map.get("maxHealth"));
        assertEquals(50.0, map.get("xp"));
        assertEquals(0.0, map.get("oceanSurvival"));
        assertEquals(0.0, map.get("maxOceanSurvival"));
        assertEquals(20L, map.get("abilityCooldownTicks"));
    }
}
