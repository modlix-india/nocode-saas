package com.fincity.saas.message.controller.message.provider.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.message.model.message.whatsapp.graph.FileHandle;
import com.fincity.saas.message.model.message.whatsapp.graph.UploadSessionId;
import com.fincity.saas.message.model.message.whatsapp.graph.UploadStatus;
import com.fincity.saas.message.model.request.message.provider.whatsapp.graph.UploadRequest;
import com.fincity.saas.message.model.request.message.provider.whatsapp.graph.UploadSessionRequest;
import com.fincity.saas.message.service.message.provider.whatsapp.WhatsappUploadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * WhatsApp configuration, restricted to tenant owners.
 *
 * <p>Annotated on the controller rather than the service, which is the usual place in this
 * codebase, because these services are shared with the inbound webhook path. That path runs
 * with no user at all, so a class-level rule on the service would gate HTTP access and break
 * message delivery at the same time. The controller is the boundary that only humans cross.
 *
 * <p>Annotated per method, never on the class. A class-level rule applies to every public method
 * including the inherited {@code @InitBinder}, and reactive method security only supports methods
 * returning a {@code Publisher}, so a {@code void} binder callback makes every request to this
 * controller fail with a 500 before it is even routed. It fails for owners too, so it is not the
 * kind of mistake that shows up only in an access test.
 *
 * <p>What that leaves open is deliberate rather than incidental. The declared methods here are all
 * administrative writes and carry the gate; the generic read endpoints inherited from
 * {@code BaseUpdatableController} do not, because the deal profile calls them as an ordinary sales
 * agent to list approved templates and business numbers in order to send a message. Reading the
 * templates you are allowed to send is deal work, not settings administration. Those reads are
 * closed by moving them behind entity-processor and blocking {@code /api/message/**} at nginx, not
 * by an authority a salesperson will never hold.
 */
@RestController
@RequestMapping("/api/message/whatsapp/uploads")
public class WhatsappUploadController {

    private final WhatsappUploadService service;

    private final ObjectMapper objectMapper;

    public WhatsappUploadController(WhatsappUploadService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    @PostMapping("/session")
    public Mono<ResponseEntity<UploadSessionId>> startUploadSession(
            @RequestBody UploadSessionRequest uploadSessionRequest) {
        return this.service.startUploadSession(uploadSessionRequest).map(ResponseEntity::ok);
    }

    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    @PostMapping
    public Mono<ResponseEntity<FileHandle>> startOrResumeUpload(
            @RequestPart(name = "file") Mono<FilePart> filePart,
            @RequestPart(name = "uploadRequestString") String uploadRequestString) {
        return this.service
                .startOrResumeUpload(this.toUploadRequest(uploadRequestString), filePart)
                .map(ResponseEntity::ok);
    }

    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    @PostMapping("/status")
    public Mono<ResponseEntity<UploadStatus>> getUploadStatus(@RequestBody UploadRequest uploadRequest) {
        return this.service.getUploadStatus(uploadRequest).map(ResponseEntity::ok);
    }

    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    @PostMapping("/resume")
    public Mono<ResponseEntity<FileHandle>> resumeUploadFromStatus(
            @RequestPart(name = "file") Mono<FilePart> filePart,
            @RequestPart(name = "uploadRequestString") String uploadRequestString) {
        return this.service
                .startOrResumeUpload(this.toUploadRequest(uploadRequestString), filePart)
                .map(ResponseEntity::ok);
    }

    private UploadRequest toUploadRequest(String uploadRequest) {
        try {
            return objectMapper.readValue(uploadRequest, UploadRequest.class);
        } catch (Exception e) {
            throw new GenericException(
                    HttpStatus.BAD_REQUEST, "Failed to parse uploadRequest into UploadRequest object", e);
        }
    }
}
