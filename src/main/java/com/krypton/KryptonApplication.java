package com.krypton;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class KryptonApplication {
    public static void main(String[] args) {
        SpringApplication.run(KryptonApplication.class, args);
    }
}