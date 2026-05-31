package com.lumoxu.cof.api.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

@RestController
@RequestMapping("/api/v1/fonts")
public class FontController {

    @Value("${cof.resource-root:../cof-resource}")
    private String resourceRoot;

    @GetMapping("/god-name-subset.woff2")
    public ResponseEntity<Resource> godNameSubset(@RequestParam(value = "text", required = false) String text) {
        Path intro = Path.of(resourceRoot, "assets", "fonts", "source-han-serif-sc-intro.woff2");
        if (!intro.toFile().exists()) {
            Path legacy = Path.of(resourceRoot).getParent().resolve("old").resolve("public")
                    .resolve("assets").resolve("fonts").resolve("source-han-serif-sc-intro.woff2");
            if (legacy.toFile().exists()) {
                intro = legacy;
            }
        }
        Resource resource = new FileSystemResource(intro);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .contentType(MediaType.parseMediaType("font/woff2"))
                .body(resource);
    }
}
