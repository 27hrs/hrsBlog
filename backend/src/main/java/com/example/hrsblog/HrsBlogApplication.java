package com.example.hrsblog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HrsBlogApplication {

    public static void main(String[] args) {
        SpringApplication.run(HrsBlogApplication.class, args);
    }

}
