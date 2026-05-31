package com.lumoxu.cof.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.domain.entity.CofCard;
import com.lumoxu.cof.domain.entity.CofDeck;
import com.lumoxu.cof.domain.mapper.CofCardMapper;
import com.lumoxu.cof.domain.mapper.CofDeckMapper;
import com.lumoxu.cof.domain.mapper.CofDeckPmvMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeckCatalogImportServiceTest {

    @Mock
    private CofDeckMapper deckMapper;
    @Mock
    private CofDeckPmvMapper deckPmvMapper;
    @Mock
    private CofCardMapper cardMapper;
    @Mock
    private DeckCatalogService deckCatalogService;

    @Test
    void importsDeckFromManifestAndMovesCards(@TempDir Path temp) throws Exception {
        Path deckDir = temp.resolve("cards").resolve("demo-deck");
        Files.createDirectories(deckDir.resolve("cards"));
        Files.writeString(deckDir.resolve("manifest.json"), """
                {"name":"Demo","curator":"Test","pmvs":[{"pmv_id":1,"name":"PMV One"}]}
                """);
        Files.write(deckDir.resolve("back.png"), new byte[]{1});
        Files.write(deckDir.resolve("cards").resolve("1a.jpg"), new byte[]{2});

        when(deckMapper.findByFolderName("demo-deck")).thenReturn(null);
        doAnswer(invocation -> {
            CofDeck deck = invocation.getArgument(0);
            deck.id = 1L;
            return 1;
        }).when(deckMapper).insert(any(CofDeck.class));

        ResourcePathMigration migration = new ResourcePathMigration();
        DeckCatalogImportService importer = new DeckCatalogImportService(
                new ObjectMapper(),
                temp.toString(),
                deckMapper,
                deckPmvMapper,
                cardMapper,
                migration,
                deckCatalogService);

        int imported = importer.importDeck(deckDir);
        assertEquals(1, imported);
        verify(deckMapper, atLeastOnce()).insert(any(CofDeck.class));
        verify(cardMapper, atLeastOnce()).insert(any(CofCard.class));
        assertTrue(Files.exists(temp.resolve("cards").resolve("1").resolve("1").resolve("a.jpg")));
    }
}
