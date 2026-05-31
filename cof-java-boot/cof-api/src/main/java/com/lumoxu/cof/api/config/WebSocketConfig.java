package com.lumoxu.cof.api.config;

import com.lumoxu.cof.api.ws.GameWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.Map;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler gameWebSocketHandler;

    public WebSocketConfig(GameWebSocketHandler gameWebSocketHandler) {
        this.gameWebSocketHandler = gameWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gameWebSocketHandler, "/ws/v1/game")
                .addInterceptors(new HttpSessionHandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(
                            org.springframework.http.server.ServerHttpRequest request,
                            org.springframework.http.server.ServerHttpResponse response,
                            org.springframework.web.socket.WebSocketHandler wsHandler,
                            Map<String, Object> attributes) throws Exception {
                        String query = request.getURI().getQuery();
                        if (query != null) {
                            for (String part : query.split("&")) {
                                String[] kv = part.split("=", 2);
                                if (kv.length == 2 && "token".equals(kv[0])) {
                                    attributes.put("token", kv[1]);
                                }
                            }
                        }
                        return super.beforeHandshake(request, response, wsHandler, attributes);
                    }
                })
                .setAllowedOrigins("*");
    }
}
