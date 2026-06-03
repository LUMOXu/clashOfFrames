package com.lumoxu.cof.domain.mapper;

import com.lumoxu.cof.boot.support.ContractTestConfig;
import com.lumoxu.cof.domain.entity.CofDeck;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(classes = com.lumoxu.cof.boot.CofApplication.class)
@ActiveProfiles("test")
@Import(ContractTestConfig.class)
class CofDeckMapperTest {

    @Autowired
    private CofDeckMapper deckMapper;

    @Test
    void listPlayableDecksIncludesSeedData() {
        List<CofDeck> decks = deckMapper.listPlayableDecks();
        assertFalse(decks.isEmpty());
    }
}
