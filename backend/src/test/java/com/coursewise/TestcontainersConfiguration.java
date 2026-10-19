package com.coursewise;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers configuration — spins up real Postgres and Mailpit
 * instances for all integration tests.
 *
 * <p>{@code @ServiceConnection} wires the datasource automatically.
 * Mailpit uses a {@link GenericContainer} with {@link DynamicPropertyRegistrar}
 * to feed its randomised SMTP port into {@code spring.mail.*} properties.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    @SuppressWarnings("resource")
    PostgreSQLContainer postgresContainer() {
        return (PostgreSQLContainer) new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                .withUrlParam("sslmode", "disable");
    }

    @Bean
    @SuppressWarnings("resource")
    GenericContainer<?> mailpitContainer() {
        return new GenericContainer<>(DockerImageName.parse("axllent/mailpit:latest"))
                .withExposedPorts(1025, 8025);
    }

    @Bean
    DynamicPropertyRegistrar mailpitProperties(GenericContainer<?> mailpitContainer) {
        return registry -> {
            registry.add("spring.mail.host", mailpitContainer::getHost);
            registry.add("spring.mail.port", () -> mailpitContainer.getMappedPort(1025));
        };
    }
}
