package com.lumoxu.cof.boot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class CofWebConfig implements WebMvcConfigurer {

    @Value("${cof.resource-root:../cof-resource}")
    private String resourceRoot;

    /** Comma-separated patterns, e.g. http://*:9001 for nginx front on any host. */
    @Value("${cof.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*,http://*:9001}")
    private String corsAllowedOriginPatterns;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] patterns = corsAllowedOriginPatterns.split(",");
        for (int i = 0; i < patterns.length; i++) {
            patterns[i] = patterns[i].trim();
        }
        registry.addMapping("/**")
                .allowedOriginPatterns(patterns)
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(resourceRoot).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/resource/**")
                .addResourceLocations(location + "/");
        Path cardsRoot = Path.of(resourceRoot).toAbsolutePath().normalize();
        registry.addResourceHandler("/cards/**")
                .addResourceLocations(location + "/cards/")
                .resourceChain(true)
                .addResolver(new DeckCardResourceResolver(cardsRoot));
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(location + "/assets/");
        registry.addResourceHandler("/audio/**")
                .addResourceLocations(location + "/audio/");
    }
}
