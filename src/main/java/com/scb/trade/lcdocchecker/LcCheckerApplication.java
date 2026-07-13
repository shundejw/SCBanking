package com.scb.trade.lcdocchecker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the LC Invoice Checker service.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LcCheckerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LcCheckerApplication.class, args);
    }
}
