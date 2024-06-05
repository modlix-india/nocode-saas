package com.fincity.saas.core.feign;

import java.nio.ByteBuffer;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "files")
public interface IFeignFilesService {

    @PostMapping("/api/files/internal/{resourceType}")
    public Mono<Map<String, Object>> create(@PathVariable String resourceType,
            @RequestParam String clientCode, @RequestParam boolean override, @RequestParam String filePath,
            @RequestParam String fileName, @RequestBody ByteBuffer file);
}
