package edu.university.grps;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GrpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrpsApplication.class, args);
    }
}
