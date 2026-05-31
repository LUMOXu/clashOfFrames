package com.lumoxu.cof.api.auth;

import com.lumoxu.cof.common.api.CofException;
import com.lumoxu.cof.common.api.ErrorCode;
import com.lumoxu.cof.common.auth.TokenPayload;
import com.lumoxu.cof.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final SessionService sessionService;

    public AuthInterceptor(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof org.springframework.web.method.HandlerMethod method)) {
            return true;
        }
        boolean required = method.hasMethodAnnotation(RequireAuth.class)
                || method.getBeanType().isAnnotationPresent(RequireAuth.class);
        String token = extractToken(request);
        if (!required) {
            sessionService.optionalToken(token).ifPresent(AuthContext::set);
            return true;
        }
        if (token == null || token.isBlank()) {
            throw new CofException(ErrorCode.UNAUTHORIZED, "请先登录。");
        }
        TokenPayload payload = sessionService.requireToken(token);
        AuthContext.set(payload);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    public static String extractToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return auth.substring(7).trim();
        }
        return request.getParameter("token");
    }
}
