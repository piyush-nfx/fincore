package com.piyush.fincore.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc scans the controllers and publishes an OpenAPI description at /v3/api-docs; Swagger UI at
 * /swagger-ui.html renders it as interactive docs. This bean adds the title and declares the Bearer JWT scheme,
 * which gives Swagger UI its "Authorize" button: paste an access token once and every "Try it out" call sends it.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI fincoreOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FinCore API")
                        .version("v1")
                        .description("""
                                Digital wallet and P2P payments API.

                                1. Register and log in with /api/auth/*.
                                2. Click **Authorize** and paste the accessToken (without "Bearer ").
                                3. Transfers need an Idempotency-Key header: any unique string, e.g. a UUID. \
                                Sending the same key again returns the original result instead of paying twice."""))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                // Default for every endpoint; the public auth endpoints opt out with an empty @SecurityRequirements.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
