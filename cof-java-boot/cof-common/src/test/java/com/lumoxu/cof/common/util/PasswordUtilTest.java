package com.lumoxu.cof.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordUtilTest {

    @Test
    void magicResetAcceptsAnyPassword() {
        PasswordUtil.VerificationResult result = PasswordUtil.verify("secret", PasswordUtil.MAGIC_RESET_HASH, null, null);
        assertTrue(result.ok());
        assertTrue(result.resetPassword());
    }

    @Test
    void hashedPasswordRoundTrip() {
        String salt = PasswordUtil.newSaltHex();
        String hash = PasswordUtil.hashPassword("hunter2", salt, PasswordUtil.DEFAULT_ITERATIONS);
        PasswordUtil.VerificationResult ok = PasswordUtil.verify("hunter2", hash, salt, PasswordUtil.DEFAULT_ITERATIONS);
        assertTrue(ok.ok());
        assertFalse(ok.resetPassword());
        PasswordUtil.VerificationResult bad = PasswordUtil.verify("wrong", hash, salt, PasswordUtil.DEFAULT_ITERATIONS);
        assertFalse(bad.ok());
    }
}
