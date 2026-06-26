package com.example.sendmail.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;

    private List<String> parsedOrigins;

    @PostConstruct
    public void logCorsConfiguration() {
        parsedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .toList();
        log.info("CORS allowed origins (allowCredentials=true): {}", parsedOrigins);
        // 純粋なワイルドカード（*/http://* /https://*）のみ検出。
        // https://*.example.com のようなサブドメインパターンは特定ドメインに限定されるため対象外。
        boolean hasBroadWildcard = parsedOrigins.stream()
                .anyMatch(o -> o.equals("*") || o.startsWith("http://*") || o.startsWith("https://*"));
        if (hasBroadWildcard) {
            log.warn("CORS: broad wildcard origin detected with allowCredentials=true — "
                    + "any origin can make credentialed requests. Review before deploying to production.");
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(parsedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
