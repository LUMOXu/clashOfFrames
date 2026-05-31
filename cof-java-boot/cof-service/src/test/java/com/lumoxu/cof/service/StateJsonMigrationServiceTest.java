package com.lumoxu.cof.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.domain.mapper.CofUserMapper;
import com.lumoxu.cof.domain.mapper.CofUserStatsMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class StateJsonMigrationServiceTest {

    @Mock
    private CofUserMapper userMapper;
    @Mock
    private CofUserStatsMapper statsMapper;

    @Test
    void missingFileReturnsZero() throws Exception {
        StateJsonMigrationService service = new StateJsonMigrationService(
                new ObjectMapper(), userMapper, statsMapper);
        assertEquals(0, service.importFromFile(Path.of("missing-state.json")));
    }
}
