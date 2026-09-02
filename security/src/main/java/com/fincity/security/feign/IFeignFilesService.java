package com.fincity.security.feign;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import lombok.Data;
import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "files")
public interface IFeignFilesService {

    @PostMapping("/api/files/internal/accesspath")
    public Mono<FilesAccessPath> createInternalAccessPath(@RequestBody FilesAccessPath accessPath);

    /**
     * Mint a time-limited secured-download key for an explicit, client-code-prefixed path
     * with no access check (trusted internal call). The caller MUST have already authorized
     * access. Returns the relative {@code api/files/secured/downloadFileByKey/{key}} URL.
     */
    @GetMapping("/api/files/internal/secured/createKey")
    public Mono<String> createSecuredKeyInternal(@RequestParam String filePath);

    @Data
    public static class FilesAccessPath {
        private String clientCode;
        private String accessName;
        private boolean writeAccess;
        private String path;
        private boolean allowSubPathAccess;
        private String resourceType;
    }
}
