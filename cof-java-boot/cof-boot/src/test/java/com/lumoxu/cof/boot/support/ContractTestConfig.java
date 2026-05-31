package com.lumoxu.cof.boot.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.service.redis.JsonRedisOps;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

@TestConfiguration
public class ContractTestConfig {

    @Bean
    @Primary
    JsonRedisOps contractJsonRedisOps(ObjectMapper objectMapper) {
        return ContractRedisSupport.memoryJsonRedis(objectMapper);
    }

    @Bean
    @Primary
    StringRedisTemplate contractStringRedisTemplate() {
        return org.mockito.Mockito.mock(StringRedisTemplate.class);
    }
}
