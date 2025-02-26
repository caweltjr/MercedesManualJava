package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class MercedesManualJavaApplication {
    public static void main(String[] args) {
        String port = System.getenv("PORT");
        System.out.println("PORT from env: " + port);
        SpringApplication.run(MercedesManualJavaApplication.class, args);
    }

    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> webServerFactoryCustomizer() {
        return factory -> {
            String port = System.getenv("PORT");
            int effectivePort = (port != null && !port.isEmpty()) ? Integer.parseInt(port) : 8080;
            System.out.println("Setting Tomcat port to: " + effectivePort);
            factory.setPort(effectivePort);
        };
    }
}