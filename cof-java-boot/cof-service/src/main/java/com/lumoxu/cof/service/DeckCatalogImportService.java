package com.lumoxu.cof.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Legacy filesystem import is disabled after catalog rebuild (V10).
 * Use submission API or a dedicated migration tool against {@code old_cof_*} tables.
 */
@Service
public class DeckCatalogImportService {

    private final DeckCatalogService deckCatalogService;

    public DeckCatalogImportService(DeckCatalogService deckCatalogService) {
        this.deckCatalogService = deckCatalogService;
    }

    public int importAllDecks() {
        return 0;
    }

    public int importDeck(Path libraryDir) throws IOException {
        if (libraryDir != null) {
            // no-op
        }
        return 0;
    }
}
