package com.mimope.server.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Decodes raw JSON text messages from the client into {@link InboundMessage} records.
 * <p>
 * Returns {@link Optional#empty()} for malformed or unrecognisable payloads
 * so the handler can log and ignore them without crashing.
 */
@Component
public class MessageDecoder {

    private static final Logger log = LoggerFactory.getLogger(MessageDecoder.class);
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public MessageDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Attempt to decode a raw JSON string into an {@link InboundMessage}.
     *
     * @param json the raw text payload
     * @return decoded message, or empty if the JSON is invalid or has no {@code "type"}
     */
    public Optional<InboundMessage> decode(String json) {
        if (json == null || json.isBlank()) {
            log.warn("Received empty message");
            return Optional.empty();
        }

        try {
            Map<String, Object> map = objectMapper.readValue(json, MAP_TYPE);
            Object typeObj = map.get("type");
            if (!(typeObj instanceof String type) || type.isBlank()) {
                log.warn("Message missing 'type' field: {}", truncate(json));
                return Optional.empty();
            }

            // Build payload without the "type" key
            Map<String, Object> payload = new LinkedHashMap<>(map);
            payload.remove("type");

            return Optional.of(new InboundMessage(type, Collections.unmodifiableMap(payload)));
        } catch (Exception e) {
            log.warn("Failed to decode message: {} – {}", truncate(json), e.getMessage());
            return Optional.empty();
        }
    }

    private static String truncate(String s) {
        return s.length() <= 200 ? s : s.substring(0, 200) + "…";
    }
}
