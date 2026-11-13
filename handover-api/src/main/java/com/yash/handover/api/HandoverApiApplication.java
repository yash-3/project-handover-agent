package com.yash.handover.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** REST API over the handover platform. Start it from the repository root with the .env loaded. */
@SpringBootApplication
public class HandoverApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(HandoverApiApplication.class, args);
    }
}
