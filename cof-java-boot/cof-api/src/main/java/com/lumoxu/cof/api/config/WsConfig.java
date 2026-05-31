package com.lumoxu.cof.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.api.ws.GameSyncEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WsConfig {

    @Bean
    public GameSyncEncoder gameSyncEncoder(ObjectMapper objectMapper) {
        return new GameSyncEncoder(objectMapper);
    }
}
