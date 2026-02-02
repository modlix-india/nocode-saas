package com.fincity.saas.message.feign;

import com.fincity.saas.message.oserver.files.model.FileDetail;
import java.nio.ByteBuffer;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "files")
public interface IFeignFileService {

    @PostMapping("/api/files/internal/{resourceType}")
    Mono<FileDetail> create(
            @PathVariable String resourceType,
            @RequestParam String clientCode,
            @RequestParam boolean override,
            @RequestParam String filePath,
            @RequestParam String fileName,
            @RequestBody ByteBuffer file);
}
