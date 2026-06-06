package com.lumoxu.cof.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Keeps PostgreSQL BIGSERIAL sequences aligned after rows are inserted with explicit ids
 * (default deck bootstrap, manual imports).
 */
@Service
public class CatalogSequenceSyncService {

    private static final List<String> CATALOG_TABLES = List.of("cof_deck", "cof_pmv", "cof_card");

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public CatalogSequenceSyncService(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    public void syncCatalogIdSequencesIfPostgres() {
        if (!isPostgres()) {
            return;
        }
        for (String table : CATALOG_TABLES) {
            jdbcTemplate.execute(
                    "SELECT setval(pg_get_serial_sequence('" + table + "', 'id'), "
                            + "GREATEST((SELECT COALESCE(MAX(id), 0) FROM " + table + "), 1))");
        }
    }

    private boolean isPostgres() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql");
        } catch (SQLException ex) {
            return false;
        }
    }
}
