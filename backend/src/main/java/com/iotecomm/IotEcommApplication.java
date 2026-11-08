package com.iotecomm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IotEcommApplication {
    public static void main(String[] args) {
        SpringApplication.run(IotEcommApplication.class, args);
    }
}
