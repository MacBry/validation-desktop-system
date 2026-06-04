package com.mac.bry.desktop.config;

import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;

@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            // Repair usuwa wpisy "FAILED" z tabeli flyway_schema_history
            // Pozwala to na ponowne wykonanie poprawionej migracji bez ręcznej ingerencji w SQL
            flyway.repair();
            flyway.migrate();
        };
    }
}
