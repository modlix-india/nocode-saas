package com.modlix.saas.files.controller;

import java.math.BigInteger;

import java.util.List;

import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.modlix.saas.files.dto.FilesRetentionPolicy;
import com.modlix.saas.files.model.retention.RetentionEffective;
import com.modlix.saas.files.model.retention.RetentionPreview;
import com.modlix.saas.files.service.FilesRetentionService;

/**
 * Path retention policies. Every call needs {@code Authorities.Files_RETENTION}; the service holds
 * the owner and managed-client checks.
 */
@RestController
@RequestMapping("api/files/retention")
public class FilesRetentionController {

    private final FilesRetentionService service;

    public FilesRetentionController(FilesRetentionService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<FilesRetentionPolicy>> list(@RequestParam String appCode,
            @RequestParam(required = false) String clientCode) {
        return ResponseEntity.ok(this.service.list(appCode, clientCode));
    }

    @PostMapping
    public ResponseEntity<FilesRetentionPolicy> create(@RequestBody FilesRetentionPolicy entity) {
        return ResponseEntity.ok(this.service.create(entity));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FilesRetentionPolicy> update(@PathVariable("id") BigInteger id,
            @RequestBody FilesRetentionPolicy entity) {
        // BigInteger, not ULong: this servlet service has no String -> ULong converter for path variables.
        return ResponseEntity.ok(this.service.update(ULong.valueOf(id), entity));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Boolean> delete(@PathVariable("id") BigInteger id) {
        return ResponseEntity.ok(this.service.delete(ULong.valueOf(id)));
    }

    @GetMapping("/effective")
    public ResponseEntity<List<RetentionEffective>> effective(@RequestParam String appCode,
            @RequestParam(required = false) String clientCode,
            @RequestParam(required = false) String resourceType) {
        return ResponseEntity.ok(this.service.effective(appCode, clientCode, resourceType));
    }

    @GetMapping("/preview")
    public ResponseEntity<RetentionPreview> preview(@RequestParam String appCode,
            @RequestParam String resourceType,
            @RequestParam(required = false, defaultValue = "") String path,
            @RequestParam(required = false) String clientCode,
            @RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(this.service.preview(appCode, resourceType, path, clientCode, days));
    }
}
