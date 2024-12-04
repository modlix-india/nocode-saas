package com.fincity.saas.core.feign;

import java.nio.ByteBuffer;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "files")
public interface IFeignFilesService {

        @PostMapping("/api/files/internal/{resourceType}")
        Mono<Map<String, Object>> create(
                        @PathVariable String resourceType,
                        @RequestParam String clientCode,
                        @RequestParam boolean override,
                        @RequestParam String filePath,
                        @RequestParam String fileName,
                        @RequestBody ByteBuffer file);

        @GetMapping("/api/files/internal/{resourceType}/file")
        Mono<ByteBuffer> downloadFile(
                        @PathVariable String resourceType,
                        @RequestParam String filePath,
                        @RequestParam(required = false) Integer width,
                        @RequestParam(required = false) Integer height,
                        @RequestParam(required = false, defaultValue = "false") Boolean download,
                        @RequestParam(required = false, defaultValue = "true") Boolean keepAspectRatio,
                        @RequestParam(required = false) String bandColor,
                        @RequestParam(required = false, defaultValue = "HORIZONTAL") String resizeDirection,
                        @RequestParam(required = false, defaultValue = "false") Boolean noCache,
                        @RequestParam(required = false) String name);


        @GetMapping("/api/files/internal/{resourceType}/convertToBase64")
        Mono<String> convertToBase64(
                @RequestHeader(required = false) String authorization,
                @PathVariable String resourceType,
                @RequestParam String clientCode,
                @RequestParam String url,
                @RequestParam(required = false) Boolean metadataRequired);
}
