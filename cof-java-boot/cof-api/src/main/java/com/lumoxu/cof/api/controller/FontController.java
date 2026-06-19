package com.lumoxu.cof.api.controller;

import com.lumoxu.cof.service.FontSubsetService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/v1/fonts")
public class FontController {

    private static final Logger log = LoggerFactory.getLogger(FontController.class);
    private final FontSubsetService fontSubsetService;

    public FontController(FontSubsetService fontSubsetService) {
        this.fontSubsetService = fontSubsetService;
    }

    @GetMapping("/god-name-subset.woff2")
    public ResponseEntity<Resource> godNameSubset() {
        return font(fontSubsetService.godSubset());
    }

    @GetMapping("/players/{statsId}.woff2")
    public ResponseEntity<Resource> playerNameSubset(@org.springframework.web.bind.annotation.PathVariable String statsId) {
        return font(fontSubsetService.playerSubset(statsId));
    }

    private ResponseEntity<Resource> font(java.nio.file.Path path) {
        try {
            log.debug("Serving font asset: path={}, bytes={}", path, java.nio.file.Files.size(path));
        } catch (java.io.IOException ex) {
            log.warn("Unable to inspect font asset before response: path={}", path, ex);
        }
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                .contentType(MediaType.parseMediaType("font/woff2"))
                .body(resource);
    }
}
