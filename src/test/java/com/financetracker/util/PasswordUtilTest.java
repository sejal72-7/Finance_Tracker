package com.financetracker.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordUtilTest {

    @Test
    void hashAndVerifyRoundTrip() {
        String hash1 = PasswordUtil.hashPassword("Secret123!");
        String hash2 = PasswordUtil.hashPassword("Secret123!");

        assertNotEquals(hash1, hash2, "Each password hash should use a unique random salt");
        assertTrue(PasswordUtil.verifyPassword("Secret123!", hash1));
        assertTrue(PasswordUtil.verifyPassword("Secret123!", hash2));
        assertFalse(PasswordUtil.verifyPassword("WrongPass", hash1));
        assertFalse(PasswordUtil.verifyPassword("", hash1));
        assertFalse(PasswordUtil.verifyPassword("Secret123!", "invalid-format"));
        assertFalse(PasswordUtil.verifyPassword(null, hash1));
    }
}

