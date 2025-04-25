package com.example.webhook;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class WebhookSpringbootApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebhookSpringbootApplication.class, args);
    }

    @Bean
    public CommandLineRunner run(WebhookService webhookService) {
        return args -> webhookService.processWebhook();
    }
}
