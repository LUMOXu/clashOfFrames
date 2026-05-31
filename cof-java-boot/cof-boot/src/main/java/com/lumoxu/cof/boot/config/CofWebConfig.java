package com.lumoxu.cof.boot.config;

import com.lumoxu.cof.domain.mapper.CofDeckMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class CofWebConfig implements WebMvcConfigurer {

    private final CofDeckMapper deckMapper;

    @Value("${cof.resource-root:../cof-resource}")
    private String resourceRoot;

    public CofWebConfig(CofDeckMapper deckMapper) {
        this.deckMapper = deckMapper;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:9001")
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
                .addResolver(new DeckCardResourceResolver(cardsRoot, deckMapper));
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(location + "/assets/");
        registry.addResourceHandler("/audio/**")
                .addResourceLocations(location + "/audio/");
    }
}
