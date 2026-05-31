package com.lumoxu.cof.api.auth;

import com.lumoxu.cof.common.auth.TokenPayload;

public final class AuthContext {

    private static final ThreadLocal<TokenPayload> CURRENT = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(TokenPayload payload) {
        CURRENT.set(payload);
    }

    public static TokenPayload get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
