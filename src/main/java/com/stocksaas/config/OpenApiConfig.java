package com.stocksaas.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;

/**
 * Configuration Swagger/OpenAPI pour la documentation de l'API
 */
@Configuration
public class OpenApiConfig {

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    @Bean
    public OpenAPI stockSaaSOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Stock SaaS API")
                        .description("API REST pour la gestion de stock multi-entreprises")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Support Stock SaaS")
                                .email("support@stocksaas.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://stocksaas.com")))
                .servers(List.of(
                        new Server()
                                .url(appBaseUrl)
                                .description("Serveur courant")
                ));
    }
}
