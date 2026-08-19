package com.fincity.saas.entity.processor.feign;

import com.fincity.saas.entity.processor.oserver.files.model.FileDetail;
import java.nio.ByteBuffer;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

/**
 * This service's door to file storage.
 *
 * <p>Three calls with three different authentication stories, which is why they are worth reading
 * together rather than assuming they behave alike:
 *
 * <ul>
 *   <li>{@link #create} and {@link #delete} go to {@code /api/files/internal/**}, which is
 *       permit-all inside the cluster and blocked at the edge. They carry no user, deliberately: an
 *       attachment arrives with nobody logged in, and the retention sweep runs on a schedule.
 *   <li>{@link #createSecuredAccessKey} goes to the authenticated controller and needs the caller's
 *       token forwarded by hand. Feign does not propagate the security context here - the same note
 *       appears on {@code IFeignMessageService} - so the token is an explicit header.
 * </ul>
 *
 * <p>That last one is not a chore worth removing. The files service then makes its own judgement
 * about whether the caller's client may read the path, on top of the deal-level check this service
 * has already made. The two ask different questions and neither can ask the other's.
 */
@ReactiveFeignClient(name = "files")
public interface IFeignFilesService {

    @PostMapping("/api/files/internal/{resourceType}")
    Mono<FileDetail> create(
            @PathVariable String resourceType,
            @RequestParam String clientCode,
            @RequestParam boolean override,
            @RequestParam String filePath,
            @RequestParam String fileName,
            @RequestBody ByteBuffer file);

    /**
     * Mints a short-lived URL for a stored file.
     *
     * <p>The path is appended to the URL rather than passed as a parameter, matching the controller's
     * {@code createKey/**} mapping. Returns the platform-relative download URL, not the key alone.
     */
    @GetMapping("/api/files/secured/createKey/{filePath}")
    Mono<String> createSecuredAccessKey(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("filePath") String filePath,
            @RequestParam("timeSpan") Long timeSpanSeconds);

    /**
     * Removes a stored file.
     *
     * <p>Internal because the caller is a scheduled sweep with no user context. The authenticated
     * delete on the secured controller cannot serve it for exactly that reason.
     */
    @DeleteMapping("/api/files/internal/{resourceType}")
    Mono<Boolean> delete(@PathVariable String resourceType, @RequestParam String filePath);
}
