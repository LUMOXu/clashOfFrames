package com.lumoxu.cof.service;

import com.lumoxu.cof.common.api.CofException;
import com.lumoxu.cof.common.api.ErrorCode;
import com.lumoxu.cof.common.util.PasswordUtil;
import com.lumoxu.cof.domain.entity.CofUser;
import com.lumoxu.cof.domain.mapper.CofUserMapper;
import com.lumoxu.cof.domain.mapper.CofUserStatsMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private CofUserMapper userMapper;
    @Mock
    private CofUserStatsMapper statsMapper;
    @Mock
    private SessionService sessionService;
    @Mock
    private UserStatsService userStatsService;

    @InjectMocks
    private AuthService authService;

    @Test
    void cleanNameTrimsWhitespace() {
        assertEquals("bob", AuthService.cleanName("  bob "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GOD", "god", "myGodName", "godzilla", "  myGodName  "})
    void registerRejectsGodContainingUsernameBeforeInsert(String username) {
        CofException error = assertThrows(
                CofException.class,
                () -> authService.register(username, "secret1"));

        assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode());
        assertEquals("You lowlife. 用户名不能包含 GOD（不区分大小写）。", error.getMessage());
        verify(userMapper, never()).insert(any(CofUser.class));
    }

    @Test
    void loginAllowsLegacyGodContainingUsername() {
        String username = "myGodName";
        String password = "secret1";
        CofUser user = user(username, password);
        when(userMapper.findByUsername(username)).thenReturn(Optional.of(user));
        when(sessionService.createSession(user.clientId, username)).thenReturn("token");

        Map<String, Object> response = authService.login(username, password);

        assertEquals("token", response.get("token"));
        verify(userMapper).updateById(user);
    }

    private CofUser user(String username, String password) {
        CofUser user = new CofUser();
        user.clientId = UUID.randomUUID();
        user.username = username;
        user.passwordSalt = PasswordUtil.newSaltHex();
        user.passwordIterations = 1;
        user.passwordHash = PasswordUtil.hashPassword(password, user.passwordSalt, user.passwordIterations);
        user.passwordDigest = PasswordUtil.DEFAULT_DIGEST;
        user.createdAt = System.currentTimeMillis();
        user.lastLoginAt = user.createdAt;
        return user;
    }
}
