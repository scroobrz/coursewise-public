package com.coursewise.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Configuration class for Flyway database migrations.
 * Customizes the Flyway execution to run before the EntityManager is created.
 */
@Configuration
public class FlywayConfig {

    /**
     * Configures and runs the Flyway migration programmatically.
     *
     * @param dataSource The primary application data source
     * @return The configured Flyway instance
     */
    @Bean
    public Flyway flyway(DataSource dataSource) {
        System.out.println("========== INITIALIZING FLYWAY ==========");
        Flyway fw = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .load();
        
        var result = fw.migrate();
        System.out.println("========== FLYWAY MIGRATION RESULT: " + result.migrationsExecuted + " migrations executed ==========");
        return fw;
    }

    /**
     * Ensures that the EntityManagerFactory depends on the completion of the Flyway migration.
     * This avoids JPA initializing schemas before Flyway finishes.
     *
     * @return A BeanFactoryPostProcessor that alters the dependency graph
     */
    @Bean
    public static BeanFactoryPostProcessor flywayEntityManagerFactoryDependency() {
        return beanFactory -> {
            if (beanFactory.containsBeanDefinition("entityManagerFactory")) {
                String[] dependsOn = beanFactory.getBeanDefinition("entityManagerFactory").getDependsOn();
                if (dependsOn == null) {
                    beanFactory.getBeanDefinition("entityManagerFactory").setDependsOn("flyway");
                } else {
                    String[] newDependsOn = new String[dependsOn.length + 1];
                    System.arraycopy(dependsOn, 0, newDependsOn, 0, dependsOn.length);
                    newDependsOn[dependsOn.length] = "flyway";
                    beanFactory.getBeanDefinition("entityManagerFactory").setDependsOn(newDependsOn);
                }
            }
        };
    }
}
