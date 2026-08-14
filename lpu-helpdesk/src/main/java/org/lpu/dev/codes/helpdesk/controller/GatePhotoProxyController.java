package org.lpu.dev.codes.helpdesk.controller;

import org.lpu.dev.codes.helpdesk.service.GatePhotoProxyService;
import org.lpu.dev.codes.helpdesk.service.GatePhotoProxyService.ProxiedPhoto;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/directory/photos")
public class GatePhotoProxyController {

    private final GatePhotoProxyService gatePhotoProxyService;

    public GatePhotoProxyController(GatePhotoProxyService gatePhotoProxyService) {
        this.gatePhotoProxyService = gatePhotoProxyService;
    }

    @GetMapping("/{filename}")
    public ResponseEntity<byte[]> photo(@PathVariable String filename) {
        ProxiedPhoto photo = gatePhotoProxyService.fetch(filename);
        return ResponseEntity.ok()
                .contentType(photo.mediaType())
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(6)).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(photo.bytes());
    }
}
