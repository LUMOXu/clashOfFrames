package com.lumoxu.cof.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetaServiceTest {

    @Mock
    private DeckCatalogService deckCatalogService;

    @Test
    void listLibrariesDelegatesToCatalog() {
        when(deckCatalogService.listFullLibraries()).thenReturn(List.of());
        MetaService metaService = new MetaService(deckCatalogService, System.getProperty("java.io.tmpdir"));
        assertTrue(metaService.listLibraries().isEmpty());
    }
}
