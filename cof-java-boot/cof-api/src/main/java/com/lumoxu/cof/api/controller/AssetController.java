package com.lumoxu.cof.api.controller;

import com.lumoxu.cof.api.auth.RequireAuth;
import com.lumoxu.cof.common.api.ApiResponse;
import com.lumoxu.cof.service.MetaService;
import com.lumoxu.cof.service.RoomService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final MetaService metaService;
    private final RoomService roomService;

    public AssetController(MetaService metaService, RoomService roomService) {
        this.metaService = metaService;
        this.roomService = roomService;
    }

    @GetMapping("/rooms/{roomId}")
    @RequireAuth
    public ApiResponse<Map<String, Object>> roomAssets(@PathVariable("roomId") String roomId) {
        return ApiResponse.ok(roomService.assetManifest(roomService.getRequired(roomId)));
    }

    @GetMapping("/resource/**")
    public ResponseEntity<Resource> resource(jakarta.servlet.http.HttpServletRequest request) {
        String uri = request.getRequestURI();
        String prefix = "/api/v1/assets/resource/";
        String relative = uri.substring(uri.indexOf(prefix) + prefix.length());
        Path file = metaService.getResourceRoot().resolve(relative.replace('/', java.io.File.separatorChar));
        if (!file.normalize().startsWith(metaService.getResourceRoot())) {
            return ResponseEntity.notFound().build();
        }
        if (!java.nio.file.Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(file));
    }
}
