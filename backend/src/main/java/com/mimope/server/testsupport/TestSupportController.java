package com.mimope.server.testsupport;

import com.mimope.server.game.GameRoom;
import com.mimope.server.game.PlayerEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * Test-only control endpoints used by browser end-to-end tests to make
 * otherwise-random gameplay deterministic (grant XP to trigger evolution,
 * force a kill to trigger the death flow).
 *
 * <p><strong>Security:</strong> this controller is annotated with
 * {@code @Profile("test")} and therefore is <em>only</em> registered when the
 * application is started with the {@code test} Spring profile active. It is
 * never present in the default or {@code prod} profiles, so it cannot be
 * reached in a real deployment. Do not remove the profile guard.
 */
@RestController
@RequestMapping("/test-support")
@Profile("test")
public class TestSupportController {

    private final GameRoom gameRoom;

    public TestSupportController(GameRoom gameRoom) {
        this.gameRoom = gameRoom;
    }

    /** Find a player by nickname (first match). */
    private Optional<PlayerEntity> byNickname(String nickname) {
        return gameRoom.getWorld().getPlayers().stream()
                .filter(p -> nickname.equals(p.getNickname()))
                .findFirst();
    }

    /** Grant XP to a player identified by nickname. */
    @PostMapping("/grant-xp")
    public ResponseEntity<Map<String, Object>> grantXp(
            @RequestParam String nickname,
            @RequestParam double amount) {
        return byNickname(nickname)
                .map(p -> {
                    p.addXp(amount);
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "nickname", nickname, "xp", p.getXp()));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Force-kill a player identified by nickname (simulates being eaten). */
    @PostMapping("/kill")
    public ResponseEntity<Map<String, Object>> kill(@RequestParam String nickname) {
        return byNickname(nickname)
                .map(p -> {
                    gameRoom.forceKill(p.getId());
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "nickname", nickname, "alive", p.isAlive()));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
