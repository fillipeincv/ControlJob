package com.controljob;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ControlJobApplication {

    public static void main(String[] args) {
        SpringApplication.run(ControlJobApplication.class, args);
    }
}
