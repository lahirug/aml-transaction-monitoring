package com.seb.aml;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the AML Transaction Screening Service.
 *
 * <p>This application evaluates financial transactions against configurable AML detection
 * rules and returns screening decisions (CLEAR or REVIEW). Rules are loaded from YAML
 * configuration at startup.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AmlScreeningApplication {

    public static void main(String[] args) {
        SpringApplication.run(AmlScreeningApplication.class, args);
    }
}
