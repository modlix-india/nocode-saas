package com.fincity.saas.commons.configuration;

import org.springframework.context.annotation.Bean;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

public interface IOpenApi30Config {

    String OPEN_API_TOKEN_NAME = "Bearer Token";

    default String getOpenApiDesc() {
        return "Modlix No-Code API";
    }

    @Bean
    default OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(
                                OPEN_API_TOKEN_NAME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .in(SecurityScheme.In.HEADER)
                                        .name("Authorization")))
                .info(new Info().title("Modlix No-Code").description(getOpenApiDesc()))
                .addSecurityItem(new SecurityRequirement().addList(OPEN_API_TOKEN_NAME));
    }
}
