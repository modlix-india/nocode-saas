package com.fincity.saas.message.controller.message.provider.whatsapp;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.message.model.message.whatsapp.graph.FileHandle;
import com.fincity.saas.message.model.message.whatsapp.graph.UploadSessionId;
import com.fincity.saas.message.model.message.whatsapp.graph.UploadStatus;
import com.fincity.saas.message.model.request.message.provider.whatsapp.graph.UploadRequest;
import com.fincity.saas.message.model.request.message.provider.whatsapp.graph.UploadSessionRequest;
import com.fincity.saas.message.service.message.provider.whatsapp.WhatsappUploadService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/message/whatsapp/uploads")
public class WhatsappUploadController {

    private final WhatsappUploadService service;

    private final ObjectMapper objectMapper;

    public WhatsappUploadController(WhatsappUploadService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/session")
    public Mono<ResponseEntity<UploadSessionId>> startUploadSession(
            @RequestBody UploadSessionRequest uploadSessionRequest) {
        return this.service.startUploadSession(uploadSessionRequest).map(ResponseEntity::ok);
    }

    @PostMapping
    public Mono<ResponseEntity<FileHandle>> startOrResumeUpload(
            @RequestPart(name = "file") Mono<FilePart> filePart, @RequestPart(name = "request") String uploadRequest) {
        try {
            UploadRequest requestObject = objectMapper.readValue(uploadRequest, UploadRequest.class);
            return this.service.startOrResumeUpload(requestObject, filePart).map(ResponseEntity::ok);
        } catch (Exception e) {
            throw new GenericException(
                    HttpStatus.BAD_REQUEST, "Failed to deserialize uploadRequest into UploadRequest object", e);
        }
    }

    @PostMapping("/status")
    public Mono<ResponseEntity<UploadStatus>> getUploadStatus(@RequestBody UploadRequest uploadRequest) {
        return this.service.getUploadStatus(uploadRequest).map(ResponseEntity::ok);
    }

    @PostMapping("/resume")
    public Mono<ResponseEntity<FileHandle>> resumeUploadFromStatus(
            @RequestPart(name = "file") Mono<FilePart> filePart, @RequestPart(name = "request") String uploadRequest) {
        try {
            UploadRequest requestObject = objectMapper.readValue(uploadRequest, UploadRequest.class);
            return this.service.startOrResumeUpload(requestObject, filePart).map(ResponseEntity::ok);
        } catch (Exception e) {
            throw new GenericException(
                    HttpStatus.BAD_REQUEST, "Failed to deserialize uploadRequest into UploadRequest object", e);
        }
    }
}
