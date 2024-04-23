package com.fincity.security.feign;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import lombok.Data;
import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "files")
public interface IFeignFilesService {

    @PostMapping("/api/files/internal/accesspath")
    public Mono<FilesAccessPath> createInternalAccessPath(@RequestBody FilesAccessPath accessPath);

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
