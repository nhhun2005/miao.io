package com.mimope.server.websocket;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Validates and sanitises player nicknames.
 * <p>
 * Rules (matching frontend {@code env.ts} constraints):
 * <ul>
 *   <li>Length between {@value #MIN_LENGTH} and {@value #MAX_LENGTH} characters (after trim).</li>
 *   <li>Only word characters, spaces, hyphens, and underscores are allowed.</li>
 *   <li>Leading/trailing whitespace is stripped.</li>
 * </ul>
 */
@Component
public class NicknameValidator {

    public static final int MIN_LENGTH = 1;
    public static final int MAX_LENGTH = 16;

    /** Allows word characters (letters, digits, underscore), spaces, and hyphens. */
    private static final Pattern ALLOWED = Pattern.compile("^[\\w\\s\\-]+$", Pattern.UNICODE_CHARACTER_CLASS);

    /**
     * Validate and sanitise the given nickname.
     *
     * @param raw the raw nickname string from the client
     * @return the trimmed nickname wrapped in an {@link Optional}, or empty if invalid
     */
    public Optional<String> validate(String raw) {
        if (raw == null) {
            return Optional.empty();
        }

        String trimmed = raw.trim();

        if (trimmed.length() < MIN_LENGTH || trimmed.length() > MAX_LENGTH) {
            return Optional.empty();
        }

        if (!ALLOWED.matcher(trimmed).matches()) {
            return Optional.empty();
        }

        return Optional.of(trimmed);
    }
}
