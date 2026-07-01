package com.mimope.server.websocket;

import com.mimope.server.game.GameRoom;
import com.mimope.server.game.PlayerEntity;
import com.mimope.server.protocol.ProtocolConstants;
import com.mimope.server.protocol.inbound.EvolveMessage;
import com.mimope.server.protocol.inbound.InputMessage;
import com.mimope.server.protocol.inbound.JoinMessage;
import com.mimope.server.protocol.inbound.PingMessage;
import com.mimope.server.protocol.outbound.ErrorMessage;
import com.mimope.server.protocol.outbound.PongMessage;
import com.mimope.server.protocol.outbound.WelcomeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.Optional;

/**
 * Central WebSocket handler for the {@code /ws/game} endpoint.
 * <p>
 * Responsibilities at this stage (Phase 6):
 * <ul>
 *   <li>Connection lifecycle management (open / close / error logging)</li>
 *   <li>Session registration and cleanup</li>
 *   <li>Inbound JSON message decoding and routing</li>
 *   <li>Join flow with nickname validation</li>
 *   <li>Ping / pong round-trip</li>
 *   <li>Unknown and malformed message handling</li>
 * </ul>
 * Game-logic message types ({@code input}, {@code evolve}, etc.) will be
 * wired in later phases.
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final SessionRegistry sessionRegistry;
    private final MessageDecoder messageDecoder;
    private final MessageEncoder messageEncoder;
    private final NicknameValidator nicknameValidator;
    private final GameRoom gameRoom;

    public GameWebSocketHandler(SessionRegistry sessionRegistry,
                                 MessageDecoder messageDecoder,
                                 MessageEncoder messageEncoder,
                                 NicknameValidator nicknameValidator,
                                 GameRoom gameRoom) {
        this.sessionRegistry = sessionRegistry;
        this.messageDecoder = messageDecoder;
        this.messageEncoder = messageEncoder;
        this.nicknameValidator = nicknameValidator;
        this.gameRoom = gameRoom;
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        ClientSession clientSession = new ClientSession(session);
        sessionRegistry.add(clientSession);
        log.info("WebSocket connected: id={}, remote={}", session.getId(), session.getRemoteAddress());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        ClientSession removed = sessionRegistry.remove(sessionId);
        // Clean up player entity from game world
        gameRoom.removePlayer(sessionId);
        log.info("WebSocket closed: id={}, nickname={}, status={}",
                sessionId,
                removed != null ? removed.getNickname() : "n/a",
                status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String sessionId = session.getId();
        log.warn("WebSocket transport error: id={}, error={}", sessionId, exception.getMessage());
        sessionRegistry.remove(sessionId);
        gameRoom.removePlayer(sessionId);
    }

    // ------------------------------------------------------------------ message dispatch

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String sessionId = session.getId();
        ClientSession clientSession = sessionRegistry.get(sessionId);
        if (clientSession == null) {
            log.warn("Received message from unregistered session: {}", sessionId);
            return;
        }

        Optional<InboundMessage> decoded = messageDecoder.decode(message.getPayload());
        if (decoded.isEmpty()) {
            messageEncoder.send(clientSession, ErrorMessage.TYPE,
                    new ErrorMessage("Malformed message").toMap());
            return;
        }

        InboundMessage msg = decoded.get();
        switch (msg.type()) {
            case ProtocolConstants.TYPE_JOIN   -> handleJoin(clientSession, msg);
            case ProtocolConstants.TYPE_INPUT  -> handleInput(clientSession, msg);
            case ProtocolConstants.TYPE_EVOLVE -> handleEvolve(clientSession, msg);
            case ProtocolConstants.TYPE_PING   -> handlePing(clientSession, msg);
            default                            -> handleUnknown(clientSession, msg);
        }
    }

    // ------------------------------------------------------------------ handlers

    private void handleJoin(ClientSession session, InboundMessage msg) {
        JoinMessage join = JoinMessage.from(msg);
        if (join == null) {
            sendError(session, "Missing nickname field.");
            return;
        }

        Optional<String> validNickname = nicknameValidator.validate(join.nickname());
        if (validNickname.isEmpty()) {
            log.info("Join rejected – invalid nickname '{}' from session {}", join.nickname(), session.getId());
            sendError(session, "Invalid nickname. Use 1-16 alphanumeric characters.");
            return;
        }

        String nickname = validNickname.get();

        // Spawn the player in the game world
        PlayerEntity player = gameRoom.addPlayer(session.getId(), nickname);
        if (player == null) {
            log.warn("Join rejected – room full for session {}", session.getId());
            sendError(session, "Room is full. Please try again later.");
            return;
        }

        session.setNickname(nickname);
        log.info("Player joined: session={}, nickname='{}', pos=({}, {})",
                session.getId(), nickname, player.getX(), player.getY());

        WelcomeMessage welcome = new WelcomeMessage(session.getId(), nickname);
        messageEncoder.send(session, WelcomeMessage.TYPE, welcome.toMap());
    }

    private void handleInput(ClientSession session, InboundMessage msg) {
        InputMessage input = InputMessage.from(msg);
        if (input == null) {
            log.debug("Malformed input message from session {}", session.getId());
            sendError(session, "Malformed input message: missing seq or angle.");
            return;
        }

        // Queue the input for the game loop to process on the next tick
        gameRoom.queueInput(session.getId(), input);

        log.trace("Input from {}: seq={}, angle={}, boost={}",
                session.getId(), input.seq(), input.angle(), input.boost());
    }

    private void handleEvolve(ClientSession session, InboundMessage msg) {
        EvolveMessage evolve = EvolveMessage.from(msg);
        if (evolve == null) {
            sendError(session, "Malformed evolve message: missing animalId.");
            return;
        }
        // Evolution will be validated and applied in Phase 12.
        log.debug("Evolve request from {}: animalId='{}'", session.getId(), evolve.animalId());
    }

    private void handlePing(ClientSession session, InboundMessage msg) {
        session.setLastPongAt(Instant.now());

        PingMessage ping = PingMessage.from(msg);
        PongMessage pong = new PongMessage(ping.timestamp());
        messageEncoder.send(session, PongMessage.TYPE, pong.toMap());
    }

    private void handleUnknown(ClientSession session, InboundMessage msg) {
        log.debug("Unknown message type '{}' from session {}", msg.type(), session.getId());
        sendError(session, "Unknown message type: " + msg.type());
    }

    // ------------------------------------------------------------------ helpers

    private void sendError(ClientSession session, String message) {
        messageEncoder.send(session, ErrorMessage.TYPE, new ErrorMessage(message).toMap());
    }
}
