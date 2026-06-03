package com.lumoxu.cof.service;

import com.lumoxu.cof.domain.mapper.CofCardMapper;
import com.lumoxu.cof.domain.mapper.CofDeckMapper;
import com.lumoxu.cof.domain.mapper.CofPmvMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DeckCatalogImportServiceTest {

    @Mock
    private DeckCatalogService deckCatalogService;
    @Mock
    private CofDeckMapper deckMapper;
    @Mock
    private CofPmvMapper pmvMapper;
    @Mock
    private CofCardMapper cardMapper;

    @Test
    void importDeckReturnsZeroWhenManifestMissing() throws Exception {
        DeckCatalogImportService service = new DeckCatalogImportService(
                new com.fasterxml.jackson.databind.ObjectMapper(),
                "/tmp/empty-resource",
                deckMapper,
                pmvMapper,
                cardMapper,
                new ResourcePathMigration("/tmp/empty-resource"),
                deckCatalogService);
        assertEquals(0, service.importDeck(Path.of("/tmp/empty-resource/cards/none")));
        verifyNoInteractions(deckMapper);
    }
}
