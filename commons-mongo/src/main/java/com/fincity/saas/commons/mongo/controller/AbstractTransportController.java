package com.fincity.saas.commons.mongo.controller;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.document.Transport;
import com.fincity.saas.commons.mongo.model.TransportRequest;
import com.fincity.saas.commons.mongo.repository.TransportRepository;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractTransportService;
import com.fincity.saas.commons.util.LogUtil;

import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.util.context.Context;

public abstract class AbstractTransportController
        extends AbstractOverridableDataController<Transport, TransportRepository, AbstractTransportService> {

    private final ObjectMapper mapper;
    private final AbstractMongoMessageResourceService messageService;

    protected AbstractTransportController(ObjectMapper objectMapper,
            AbstractMongoMessageResourceService messageService) {
        this.mapper = objectMapper;
        this.messageService = messageService;
    }

    @PostMapping("/makeTransport")
    public Mono<Void> makeTransport(@RequestBody TransportRequest request, ServerHttpResponse response) {

        return this.service.makeTransport(false, request, response);
    }

    @PostMapping("/internal/makeTransport")
    public Mono<Void> internalMakeTransport(@RequestBody TransportRequest request, ServerHttpResponse response) {

        return this.service.makeTransport(true, request, response);
    }

    @GetMapping("/applyTransport/{id}")
    public Mono<ResponseEntity<Boolean>> applyTransport(@RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort, @PathVariable("id") String transportId) {

        return this.service.applyTransport(forwardedHost, forwardedPort, transportId, null, false)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/applyTransportCode/{code}")
    public Mono<ResponseEntity<Boolean>> applyTransportWithTransportCode(
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort, @PathVariable("code") String code) {

        return this.service.applyTransportWithTransportCode(forwardedHost, forwardedPort, code)
                .map(ResponseEntity::ok);
    }

    /**
     * Drops import receipts older than the retention window.
     *
     * <p>Driven by the worker, which is the only thing here that can run a job
     * once across several instances. A {@code @Scheduled} in this service would
     * fire on every replica at the same moment and have them racing to delete the
     * same documents.
     *
     * <p>On the internal path, which in this service means unauthenticated: the ui
     * service passes {@code "/**"} to springSecurityFilterChain, so nothing here
     * is gated at the filter chain and nginx is the boundary. That is the same
     * posture as {@code /api/files/internal/{type}/cleanupExpired}, which deletes
     * files. What keeps it defensible is that the damage an unexpected caller
     * could do is bounded by construction rather than by who they are: this can
     * only ever remove Transport documents, never a definition, and the retention
     * floor and per-run cap live in the service, so the worst an arbitrary caller
     * achieves is running the sweep early.
     */
    @PostMapping("/internal/cleanupOlderThan")
    public Mono<ResponseEntity<Map<String, Integer>>> internalCleanupOlderThan(
            @RequestParam(required = false, defaultValue = "90") int retentionDays,
            @RequestParam(required = false, defaultValue = "200") int limit) {

        return this.service.cleanupOlderThan(retentionDays, limit).map(ResponseEntity::ok);
    }

    @GetMapping("/transportTypes")
    public Mono<ResponseEntity<List<String>>> transportTypes() {
        return Mono.just(ResponseEntity.ok(this.service.getServieMap()
                .stream()
                .map(e -> e.getObjectName())
                .toList()));
    }

    @Override
    public Mono<ResponseEntity<Transport>> create(Transport entity) {
        return this.messageService
                .throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        AbstractMongoMessageResourceService.TRANSPORT_ERROR, "Creating",
                        "You need to upload a transport file using /createAndApply");
    }

    @PostMapping("/internal/createAndApply")
    public Mono<ResponseEntity<Transport>> internalCreateAndApply(
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestParam(defaultValue = "false") Boolean isForBaseApp,
            @RequestParam(required = false) String applicationCode,
            @RequestParam(defaultValue = "false") Boolean isJson,
            @RequestParam String fileName,
            ServerHttpRequest request) {

        return FlatMapUtil.flatMapMono(

                () -> {
                    try {
                        Path folder = Files.createTempDirectory("transport");
                        Path file = folder.resolve(fileName);
                        return DataBufferUtils
                                .write(request.getBody(), file, StandardOpenOption.CREATE,
                                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
                                .then(Mono.just(file));
                    } catch (Exception e) {
                        return this.messageService
                                .throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg, e),
                                        AbstractMongoMessageResourceService.TRANSPORT_ERROR, "Creating",
                                        e.getMessage());
                    }
                },

                file -> handleTransportData(isJson, fileName, file),

                (file, transport) -> this.service.applyTransport(forwardedHost, forwardedPort, transport.getId(),
                        applicationCode, isForBaseApp),

                (file, transport, applied) -> Mono.just(ResponseEntity.ok(transport)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractTransportController.internalCreateAndApply"));
    }

    private Mono<Transport> handleTransportData(Boolean isJson, String fileName, Path file) {
        try {

            Consumer<SignalType> removeFiles = x -> {
                try {
                    Files.deleteIfExists(file);
                    Files.deleteIfExists(file.getParent());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            };

            if (isJson.booleanValue() || fileName.endsWith(".json")) {

                Transport entity = this.mapper.readValue(file.toFile(), Transport.class);
                return this.service.create(entity).doFinally(removeFiles);
            }

            FileSystem zipfs = FileSystems.newFileSystem(URI.create("jar:" + file.toUri().toString()),
                    Map.of());
            Path json = zipfs.getPath("transport.json");

            Transport entity = this.mapper.readValue(Files.readString(json), Transport.class);
            entity.setEncodedModl(java.util.Base64.getEncoder().encodeToString(Files.readAllBytes(file)));
            return this.service.create(entity).doFinally(removeFiles);
        } catch (Exception e) {
            return this.messageService
                    .throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg, e),
                            AbstractMongoMessageResourceService.TRANSPORT_ERROR, "Creating",
                            e.getMessage());
        }
    }

    @PostMapping("/createAndApply")
    public Mono<ResponseEntity<Transport>> createApply(
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestParam(defaultValue = "false") Boolean isForBaseApp,
            @RequestParam(required = false) String applicationCode,
            @RequestParam(defaultValue = "false") Boolean isJson,
            @RequestPart(name = "file") Mono<FilePart> filePart) {

        return FlatMapUtil.flatMapMono(

                () -> filePart,

                fp -> {
                    try {
                        Path folder = Files.createTempDirectory("transport");
                        Path file = folder.resolve(fp.filename());
                        return fp.transferTo(file).then(Mono.just(file));
                    } catch (Exception e) {
                        return this.messageService
                                .throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg, e),
                                        AbstractMongoMessageResourceService.TRANSPORT_ERROR, "Creating",
                                        e.getMessage());
                    }
                },

                (fp, file) -> handleTransportData(isJson, fp.filename(), file),

                (fp, file, transport) -> this.service.applyTransport(forwardedHost, forwardedPort, transport.getId(),
                        applicationCode, isForBaseApp),

                (fp, file, transport, applied) -> Mono.just(ResponseEntity.ok(transport)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractTransportController.createApply"));

    }
}
