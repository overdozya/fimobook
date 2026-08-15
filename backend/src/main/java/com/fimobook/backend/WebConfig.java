package com.fimobook.backend;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AssetUrlResolver assetUrlResolver;

    public WebConfig(AssetUrlResolver assetUrlResolver) {
        this.assetUrlResolver = assetUrlResolver;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry
                .addMapping("/api/**")
                .allowedOrigins(
                        "http://localhost:5173",
                        "http://127.0.0.1:5173",
                        "http://localhost:5174",
                        "http://127.0.0.1:5174",
                        "http://localhost:8081",
                        "http://127.0.0.1:8081",
                        "http://localhost:19006",
                        "http://127.0.0.1:19006")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = assetUrlResolver.localRoot().toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/api/assets/**")
                .addResourceLocations(location)
                .setCachePeriod(86400);
    }
}
