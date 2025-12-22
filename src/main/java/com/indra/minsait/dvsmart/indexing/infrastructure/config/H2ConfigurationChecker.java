package com.indra.minsait.dvsmart.indexing.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 22-12-2025 at 15:41:23
 * File: H2ConfigurationChecker.java
 */

/**
 * Verifica la configuración de H2 al iniciar la aplicación.
 */
@Slf4j
@Component
public class H2ConfigurationChecker implements ApplicationRunner {

    @Autowired
    private DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("════════════════════════════════════════════════════");
        log.info("H2 DATABASE CONFIGURATION CHECK");
        log.info("════════════════════════════════════════════════════");

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            
            log.info("Database Product: {}", metaData.getDatabaseProductName());
            log.info("Database Version: {}", metaData.getDatabaseProductVersion());
            log.info("Database URL: {}", metaData.getURL());
            log.info("Driver Name: {}", metaData.getDriverName());
            log.info("Driver Version: {}", metaData.getDriverVersion());

            // Verificar tablas de Spring Batch
            log.info("\n--- Spring Batch Tables ---");
            ResultSet tables = metaData.getTables(null, null, "BATCH_%", new String[]{"TABLE"});
            
            boolean foundTables = false;
            int tableCount = 0;
            while (tables.next()) {
                foundTables = true;
                String tableName = tables.getString("TABLE_NAME");
                log.info("  ✓ {}", tableName);
                tableCount++;
            }

            if (!foundTables) {
                log.warn("⚠️  NO Spring Batch tables found!");
                log.warn("⚠️  Check 'spring.batch.jdbc.initialize-schema' property");
            } else {
                log.info("\n✅ Found {} Spring Batch tables", tableCount);
            }

        } catch (Exception e) {
            log.error("❌ Error checking H2 configuration", e);
        }

        log.info("════════════════════════════════════════════════════");
    }
}
