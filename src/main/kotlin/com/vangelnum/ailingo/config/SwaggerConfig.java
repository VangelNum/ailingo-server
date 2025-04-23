package com.vangelnum.ailingo.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.servers.Server;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@SecurityScheme(
        name = "basicAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "basic"
)
@RequiredArgsConstructor
public class SwaggerConfig {

    private final ValuesService valuesService;

    @Bean
    public GroupedOpenApi restApi() {
        return GroupedOpenApi.builder()
                .displayName("api")
                .group("rest")
                .pathsToMatch("/api/v1/**")
                .build();
    }

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .servers(List.of(new Server().url(valuesService.getAddress())))
                .info(new Info()
                        .title("ailingo")
                        .description("Сервисы REST Api. Для использования API с защищенными эндпоинтами," +
                                " нажмите на кнопку 'Authorize' (или иконку замка) и введите ваш" +
                                " username и password в формате Basic Auth.")
                        .version("0.1")
                        .license(null)
                )
                .addSecurityItem(new SecurityRequirement().addList("basicAuth"));
    }
}