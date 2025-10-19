package com.example.demo.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration pour Swagger/OpenAPI.
 *
 * Cette classe configure Swagger pour documenter l'API REST de l'application.
 * L'API est accessible via /swagger-ui.html ou /v3/api-docs.
 */
@Configuration
class SwaggerConfig {

    /**
     * Crée la configuration OpenAPI.
     *
     * Définit le titre, la description et la version de l'API.
     *
     * @return OpenAPI configurée.
     */
    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("GPS Server API")
                    .description("API pour gérer les dispositifs GPS selon le protocole Beesure GPS SeTracker")
                    .version("1.0.0")
            )
    }
}