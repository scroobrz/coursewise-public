package com.coursewise;

import com.coursewise.config.AppConfigProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Coursewise — University of Edinburgh course selection tool.
 * Main entry point for the Spring Boot backend application.
 */
@SpringBootApplication
@EnableConfigurationProperties(AppConfigProperties.class)
public class CoursewiseApplication {

    /**
     * Bootstraps and launches the Spring application.
     *
     * @param args Command line arguments passed to the application
     */
    public static void main(String[] args) {
        SpringApplication.run(CoursewiseApplication.class, args);
    }
}
