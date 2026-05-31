package com.lumoxu.cof.boot;

import com.lumoxu.cof.service.redis.JsonRedisOps;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CofApplicationTest {

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private JsonRedisOps jsonRedisOps;

    @Test
    void contextLoads() {
    }
}
