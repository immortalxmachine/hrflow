package com.hrflow.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;

/**
 * Base class for integration tests.
 *
 * <p>Starts ONE real PostgreSQL (zonky's auto-downloaded binary -- no Docker,
 * no system install) for the whole JVM and feeds its JDBC URL to Spring via
 * {@link DynamicPropertySource}. Spring Boot then builds a normal Hikari pool,
 * Flyway runs the production migrations, and Hibernate validates the entity
 * mapping against the result. Because we only hand Spring a JDBC URL, this is
 * independent of the Spring Boot version (unlike zonky's auto-configuration).
 *
 * <p>{@code @Transactional} rolls back each test method, so tests are isolated
 * while sharing the (cached) context and database for speed.
 */
@SpringBootTest
@Transactional
public abstract class AbstractPostgresTest {

    // Started once per JVM; EmbeddedPostgres installs its own JVM shutdown hook.
    protected static final EmbeddedPostgres POSTGRES;

    static {
        try {
            // Extract the Postgres binaries into an HRFlow-specific directory so
            // we never collide with another project that also uses zonky's
            // default %TEMP%/embedded-pg directory on the same machine.
            File workDir = new File(System.getProperty("java.io.tmpdir"), "hrflow-embedded-pg");
            POSTGRES = EmbeddedPostgres.builder()
                    .setOverrideWorkingDirectory(workDir)
                    .start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
    }
}
