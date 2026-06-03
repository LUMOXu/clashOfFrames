package com.lumoxu.cof.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class DeckCatalogImportServiceTest {

    @Mock
    private DeckCatalogService deckCatalogService;

    @Test
    void importAllDecksIsDisabledAfterRebuild() throws Exception {
        DeckCatalogImportService service = new DeckCatalogImportService(deckCatalogService);
        assertEquals(0, service.importAllDecks());
    }
}
