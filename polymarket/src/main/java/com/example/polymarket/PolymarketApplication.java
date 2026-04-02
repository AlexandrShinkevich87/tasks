package com.example.polymarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PolymarketApplication {
    public static void main(String[] args) {
        SpringApplication.run(PolymarketApplication.class, args);
    }
}
