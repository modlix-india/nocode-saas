package com.fincity.saas.message.controller.message.provider.whatsapp;

import com.fincity.saas.message.model.message.whatsapp.graph.BaseId;
import com.fincity.saas.message.model.message.whatsapp.graph.FileHandle;
import com.fincity.saas.message.model.message.whatsapp.graph.UploadStatus;
import com.fincity.saas.message.model.request.message.provider.whatsapp.graph.UploadRequest;
import com.fincity.saas.message.model.request.message.provider.whatsapp.graph.UploadSessionRequest;
import com.fincity.saas.message.service.message.provider.whatsapp.WhatsappUploadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/message/whatsapp/uploads")
public class WhatsappUploadController {

    private final WhatsappUploadService service;

    public WhatsappUploadController(WhatsappUploadService service) {
        this.service = service;
    }

    @PostMapping("/session")
    public Mono<ResponseEntity<BaseId>> startUploadSession(@RequestBody UploadSessionRequest uploadSessionRequest) {
        return this.service.startUploadSession(uploadSessionRequest).map(ResponseEntity::ok);
    }

    @PostMapping
    public Mono<ResponseEntity<FileHandle>> startOrResumeUpload(@RequestBody UploadRequest uploadRequest) {
        return this.service.startOrResumeUpload(uploadRequest).map(ResponseEntity::ok);
    }

    @PostMapping("/status")
    public Mono<ResponseEntity<UploadStatus>> getUploadStatus(@RequestBody UploadRequest uploadRequest) {
        return this.service.getUploadStatus(uploadRequest).map(ResponseEntity::ok);
    }

    @PostMapping("/resume")
    public Mono<ResponseEntity<FileHandle>> resumeUploadFromStatus(@RequestBody UploadRequest uploadRequest) {
        return this.service.resumeUploadFromStatus(uploadRequest).map(ResponseEntity::ok);
    }
}
