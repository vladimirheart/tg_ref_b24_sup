package com.example.supportbot.vk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.example.supportbot")
@EnableScheduling
public class VkBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(VkBotApplication.class, args);
    }
}
