package com.fitpilot;

import com.fitpilot.config.ArkProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@EnableConfigurationProperties(ArkProperties.class)
public class FitPilotApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(FitPilotApplication.class, args);
        ArkProperties ark = ctx.getBean(ArkProperties.class);
        System.out.println("\n========== FitPilot Ark config ==========");
        System.out.println(ark.summary());
        System.out.println("=========================================\n");
    }
}