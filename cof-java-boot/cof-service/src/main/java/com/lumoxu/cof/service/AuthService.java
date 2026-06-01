package com.lumoxu.cof.service;

import com.lumoxu.cof.common.api.CofException;
import com.lumoxu.cof.common.api.ErrorCode;
import com.lumoxu.cof.common.util.PasswordUtil;
import com.lumoxu.cof.domain.entity.CofUser;
import com.lumoxu.cof.domain.entity.CofUserStats;
import com.lumoxu.cof.domain.mapper.CofUserMapper;
import com.lumoxu.cof.domain.mapper.CofUserStatsMapper;
import com.lumoxu.cof.service.model.PublicPlayerDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private final CofUserMapper userMapper;
    private final CofUserStatsMapper statsMapper;
    private final SessionService sessionService;
    private final UserStatsService userStatsService;

    public AuthService(
            CofUserMapper userMapper,
            CofUserStatsMapper statsMapper,
            SessionService sessionService,
            UserStatsService userStatsService) {
        this.userMapper = userMapper;
        this.statsMapper = statsMapper;
        this.sessionService = sessionService;
        this.userStatsService = userStatsService;
    }

    @Transactional
    public Map<String, Object> register(String username, String password) {
        validateCredentials(username, password);
        if (userMapper.findByUsername(username).isPresent()) {
            throw new CofException(ErrorCode.CONFLICT, "用户名已被占用。");
        }
        CofUser user = createUser(username, password);
        userMapper.insert(user);
        userStatsService.ensureStats(user.clientId.toString(), user.username, false, null);
        String token = sessionService.createSession(user.clientId, user.username);
        return loginResponse(token, user, false);
    }

    @Transactional
    public Map<String, Object> login(String username, String password) {
        validateCredentials(username, password);
        CofUser user = userMapper.findByUsername(username)
                .orElseThrow(() -> new CofException(ErrorCode.UNAUTHORIZED, "用户名或密码不正确。"));
        PasswordUtil.VerificationResult verification = PasswordUtil.verify(
                password,
                user.passwordHash,
                user.passwordSalt,
                user.passwordIterations);
        if (!verification.ok()) {
            throw new CofException(ErrorCode.UNAUTHORIZED, "用户名或密码不正确。");
        }
        if (verification.resetPassword() || verification.legacyNodeHash()) {
            applyPassword(user, password);
            userMapper.updateById(user);
        }
        user.lastLoginAt = System.currentTimeMillis();
        userMapper.updateById(user);
        userStatsService.ensureStats(user.clientId.toString(), user.username, false, null);
        String token = sessionService.createSession(user.clientId, user.username);
        return loginResponse(token, user, verification.resetPassword());
    }

    public void logout(String token) {
        sessionService.revoke(token);
    }

    private Map<String, Object> loginResponse(String token, CofUser user, boolean passwordReset) {
        Map<String, Object> body = new HashMap<>();
        body.put("token", token);
        body.put("player", toPublicPlayer(user));
        if (passwordReset) {
            body.put("passwordReset", true);
        }
        return body;
    }

    public PublicPlayerDto toPublicPlayer(CofUser user) {
        PublicPlayerDto dto = new PublicPlayerDto();
        dto.clientId = user.clientId.toString();
        dto.username = user.username;
        dto.statsId = user.clientId.toString();
        dto.connected = true;
        dto.joinedAt = user.createdAt != null ? user.createdAt : System.currentTimeMillis();
        dto.lastSeenAt = System.currentTimeMillis();
        return dto;
    }

    private CofUser createUser(String username, String password) {
        CofUser user = new CofUser();
        user.clientId = UUID.randomUUID();
        user.username = cleanName(username);
        applyPassword(user, password);
        long now = System.currentTimeMillis();
        user.createdAt = now;
        user.lastLoginAt = now;
        return user;
    }

    private void applyPassword(CofUser user, String password) {
        user.passwordSalt = PasswordUtil.newSaltHex();
        user.passwordHash = PasswordUtil.hashPassword(password, user.passwordSalt, PasswordUtil.DEFAULT_ITERATIONS);
        user.passwordIterations = PasswordUtil.DEFAULT_ITERATIONS;
        user.passwordDigest = PasswordUtil.DEFAULT_DIGEST;
    }

    private void validateCredentials(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new CofException(ErrorCode.BAD_REQUEST, "username required");
        }
        if (password == null || password.length() < 6) {
            throw new CofException(ErrorCode.BAD_REQUEST, "密码至少需要 6 个字符。");
        }
    }

    public static String cleanName(String username) {
        return username.trim();
    }
}
