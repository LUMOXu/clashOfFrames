package com.lumoxu.cof.boot;

import com.lumoxu.cof.service.ComputerPlayerImportService;
import com.lumoxu.cof.service.DeckCatalogImportService;
import com.lumoxu.cof.service.StateJsonMigrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class CofImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CofImportRunner.class);

    private final DeckCatalogImportService deckCatalogImportService;
    private final ComputerPlayerImportService computerPlayerImportService;
    private final StateJsonMigrationService stateJsonMigrationService;

    public CofImportRunner(
            DeckCatalogImportService deckCatalogImportService,
            ComputerPlayerImportService computerPlayerImportService,
            StateJsonMigrationService stateJsonMigrationService) {
        this.deckCatalogImportService = deckCatalogImportService;
        this.computerPlayerImportService = computerPlayerImportService;
        this.stateJsonMigrationService = stateJsonMigrationService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try {
            int computers = computerPlayerImportService.importFromDefaultLocations();
            if (computers > 0) {
                log.info("Synced {} computer player profile(s) into PostgreSQL.", computers);
            }
        } catch (Exception ex) {
            log.warn("Computer player config sync skipped: {}", ex.getMessage());
        }
        if (args.containsOption("import-decks")) {
            int count = deckCatalogImportService.importAllDecks();
            log.info("Imported {} card deck(s) into PostgreSQL.", count);
        }
        if (args.containsOption("import-computers")) {
            int count = computerPlayerImportService.importFromDefaultLocations();
            log.info("Imported {} computer player(s) into PostgreSQL.", count);
        }
        if (args.containsOption("migrate-state-json")) {
            Path stateJson = Path.of("data/state.json").toAbsolutePath().normalize();
            int count = stateJsonMigrationService.importFromFile(stateJson);
            log.info("Migrated {} user(s) from state.json.", count);
        }
    }
}
