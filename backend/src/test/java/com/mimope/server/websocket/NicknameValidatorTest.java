package com.mimope.server.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class NicknameValidatorTest {

    private NicknameValidator validator;

    @BeforeEach
    void setUp() {
        validator = new NicknameValidator();
    }

    @Test
    void validSimpleName() {
        assertEquals(Optional.of("Player1"), validator.validate("Player1"));
    }

    @Test
    void validNameWithSpaces() {
        assertEquals(Optional.of("Cool Player"), validator.validate("Cool Player"));
    }

    @Test
    void validNameWithHyphen() {
        assertEquals(Optional.of("my-name"), validator.validate("my-name"));
    }

    @Test
    void validNameWithUnderscore() {
        assertEquals(Optional.of("my_name"), validator.validate("my_name"));
    }

    @Test
    void trimmedLeadingTrailingSpaces() {
        assertEquals(Optional.of("Player"), validator.validate("  Player  "));
    }

    @Test
    void singleCharIsValid() {
        assertEquals(Optional.of("A"), validator.validate("A"));
    }

    @Test
    void exactMaxLengthIsValid() {
        String name = "a".repeat(NicknameValidator.MAX_LENGTH);
        assertEquals(Optional.of(name), validator.validate(name));
    }

    @Test
    void exceedsMaxLengthIsInvalid() {
        String name = "a".repeat(NicknameValidator.MAX_LENGTH + 1);
        assertTrue(validator.validate(name).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void nullAndEmptyAreInvalid(String input) {
        assertTrue(validator.validate(input).isEmpty());
    }

    @Test
    void blankStringIsInvalid() {
        assertTrue(validator.validate("   ").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"<script>", "name!", "a@b", "a#b", "a$b", "a&b"})
    void specialCharsAreInvalid(String input) {
        assertTrue(validator.validate(input).isEmpty());
    }
}
