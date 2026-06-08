package com.example.sendmail.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("郵便物送付管理システム API")
                        .description("Spring Boot バックエンド REST API ドキュメント")
                        .version("1.0.0"));
    }
}
