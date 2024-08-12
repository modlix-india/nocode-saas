package com.fincity.saas.commons.mongo.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.mongo.document.Transport;
import com.fincity.saas.commons.mongo.enums.TransportFileType;
import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.model.TransportObject;
import com.fincity.saas.commons.mongo.model.TransportRequest;
import com.fincity.saas.commons.mongo.repository.TransportRepository;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.commons.util.UniqueUtil;
import java.io.File;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ZeroCopyHttpOutputMessage;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;

public abstract class AbstractTransportService extends AbstractOverridableDataService<Transport, TransportRepository> {

    private static final String TRANSPORT = "Transport";

    private final IFeignSecurityService feignSecurityService;

    protected AbstractTransportService(IFeignSecurityService feignSecurityService) {
        super(Transport.class);
        this.feignSecurityService = feignSecurityService;
    }

    @Override
    public Mono<Transport> create(Transport entity) {

        entity.setName(StringUtil.safeValueOf(entity.getName(), "") + entity.getUniqueTransportCode());

        return this
                .readAllFilter(new ComplexCondition()
                        .setConditions(List.of(new FilterCondition().setField("uniqueTransportCode")
                                .setValue(entity.getUniqueTransportCode()))))
                .collectList()
                .flatMap(e -> e.isEmpty() ? super.create(entity) : Mono.just(e.get(0)));

    }

    public Mono<Boolean> applyTransportWithTransportCode(String forwardedHost, String forwardedPort,
            String transportCode) {

        return this.readAllFilter(new ComplexCondition()
                .setConditions(List.of(new FilterCondition().setField("uniqueTransportCode")
                        .setValue(transportCode))))
                .collectList()
                .flatMap(e -> e.isEmpty() ? this.messageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                        AbstractMongoMessageResourceService.OBJECT_NOT_FOUND, TRANSPORT, transportCode)
                        : this.applyTransport(forwardedHost, forwardedPort, e.get(0).getId(), null, false));

    }

    public Mono<Boolean> applyTransport(String forwardedHost, String forwardedPort, String id,
            String transportForAppCode, boolean isBaseApp) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.read(id),

                (ca, transport) -> {

                    if (StringUtil.safeIsBlank(transportForAppCode))
                        return Mono.just(Optional.<Tuple2<String, Boolean>>empty());

                    return this.feignSecurityService
                            .findBaseClientCodeForOverride(ca.getAccessToken(), forwardedHost, forwardedPort,
                                    ca.getUrlClientCode(), ca.getUrlAppCode(), transportForAppCode)
                            .map(Optional::of).defaultIfEmpty(Optional.empty());
                },

                (ca, transport, baseCodeTup) -> {

                    boolean isModlStringEmpty = StringUtil.safeIsBlank(transport.getEncodedModl());

                    if ((transport.getObjects() == null || transport.getObjects().isEmpty())
                            && isModlStringEmpty)
                        return Mono.just(true);

                    if (isModlStringEmpty)
                        return applyTransportForJSON(transportForAppCode, isBaseApp, ca, transport, baseCodeTup);

                    return applyTransportForZip(transportForAppCode, isBaseApp, ca, transport, baseCodeTup);
                }

        )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractTransportService.applyTransport"))
                .defaultIfEmpty(false);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Mono<Boolean> applyTransportForZip(String transportForAppCode, boolean isBaseApp, ContextAuthentication ca,
            Transport transport, Optional<Tuple2<String, Boolean>> baseCodeTup) {

        try {
            Path tempDir = Files.createTempDirectory("transport");
            Path zipFile = tempDir.resolve(transport.getUniqueTransportCode() + ".zip");

            Files.write(zipFile, Base64.getDecoder().decode(transport.getEncodedModl()));

            FileSystem zipfs = FileSystems.newFileSystem(URI.create("jar:" + zipFile.toUri().toString()), Map.of());

            Map<String, AbstractOverridableDataService> serviceMap = this.getServieMap()
                    .stream()
                    .collect(Collectors.toMap(AbstractOverridableDataService::getObjectName,
                            Function.identity()));

            return Flux.fromIterable(this.getServieMap())
                    .flatMap(service -> {
                        Path dirPath = zipfs.getPath("/", service.getObjectName());
                        if (!Files.exists(dirPath))
                            return Flux.empty();
                        try {
                            return Flux.fromStream(Files.list(dirPath))
                                    .map(e -> {
                                        try {
                                            Map<String, Object> data = this.objectMapper.readValue(Files.readString(e),
                                                    Map.class);
                                            return new TransportObject().setData(data)
                                                    .setObjectType(service.getObjectName());
                                        } catch (Exception ex) {
                                            throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR,
                                                    ex.getMessage(),
                                                    ex);
                                        }
                                    });
                        } catch (Exception ex) {
                            throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
                        }
                    })
                    .flatMap(
                            obj -> {
                                AbstractOverridableDataService service = serviceMap.get(obj.getObjectType());
                                if (service == null)
                                    return Mono.empty();

                                return this.addObjectToService(service, obj, transportForAppCode, ca, baseCodeTup,
                                        transport,
                                        isBaseApp);
                            })
                    .collectList()
                    .map(e -> true)
                    .doFinally(e -> {
                        try {
                            zipfs.close();
                            // Files.deleteIfExists(zipFile);
                            // Files.deleteIfExists(tempDir);
                        } catch (Exception ex) {
                            throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
                        }
                    });
        } catch (Exception ex) {
            throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }

    @SuppressWarnings({ "rawtypes" })
    private Mono<Boolean> applyTransportForJSON(String transportForAppCode, boolean isBaseApp, ContextAuthentication ca,
            Transport transport, Optional<Tuple2<String, Boolean>> baseCodeTup) {

        var serviceMap = this.getServieMap()
                .stream()
                .collect(Collectors.toMap(AbstractOverridableDataService::getObjectName,
                        Function.identity()));

        return Flux.fromIterable(transport.getObjects())
                .flatMap(obj -> {
                    AbstractOverridableDataService service = serviceMap.get(obj.getObjectType());
                    if (service == null)
                        return Mono.empty();

                    return this.addObjectToService(service, obj, transportForAppCode, ca, baseCodeTup, transport,
                            isBaseApp);
                })
                .collectList()
                .map(e -> true);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Mono<Boolean> addObjectToService(
            AbstractOverridableDataService service,
            TransportObject transportObject,
            String transportForAppCode, ContextAuthentication ca, Optional<Tuple2<String, Boolean>> baseCodeTup,
            Transport transport, boolean isBaseApp) {

        AbstractOverridableDTO tentity = service.makeEntity(transportObject.getObjectType(),
                transportObject.getData());

        if (!StringUtil.safeIsBlank(transportForAppCode))
            tentity.setAppCode(transportForAppCode);

        if (baseCodeTup.isPresent()) {
            if (baseCodeTup.get().getT2().booleanValue()) {
                tentity.setClientCode(baseCodeTup.get().getT1());
                tentity.setBaseClientCode(null);
            } else {
                tentity.setClientCode(ca.getClientCode());
                tentity.setBaseClientCode(baseCodeTup.get().getT1());
            }
        } else {
            tentity.setClientCode(ca.getClientCode());
            tentity.setBaseClientCode(null);
        }

        tentity.setId(null);
        tentity.setMessage("From transport : " + transport.getName());
        tentity.setVersion(1);

        return service
                .read(
                        tentity.getName(), tentity.getAppCode(),
                        tentity.getClientCode())
                .map(e -> ((ObjectWithUniqueID<? extends AbstractOverridableDTO>) e)
                        .getObject())
                .flatMap(entity -> {

                    AbstractOverridableDTO sentity = (AbstractOverridableDTO) entity;

                    if (sentity != null
                            && (isBaseApp || StringUtil.safeEquals(ca.getClientCode(),
                                    sentity.getClientCode()))) {
                        tentity.setVersion(sentity.getVersion());
                        tentity.setId(sentity.getId());
                        return service.update(tentity);
                    }
                    return service.create(tentity);
                }).switchIfEmpty(Mono.defer(() -> service.create(tentity))).map(e -> true);
    }

    @Override
    public Mono<Transport> update(Transport entity) {

        return this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.NOT_MODIFIED, msg),
                AbstractMessageService.CANNOT_BE_UPDATED);
    }

    @Override
    protected Mono<Transport> updatableEntity(Transport entity) {
        return this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.NOT_MODIFIED, msg),
                AbstractMessageService.CANNOT_BE_UPDATED);
    }

    @Override
    protected Mono<String> getLoggedInUserId() {

        return SecurityContextUtil.getUsersContextAuthentication()
                .map(ContextAuthentication::getUser)
                .map(ContextUser::getId)
                .map(Object::toString);
    }

    public Mono<Void> makeTransport(TransportRequest request, ServerHttpResponse response) {

        if (request.getFileType() == null || request.getFileType() == TransportFileType.ZIP) {

            return this.makeZipTransport(request).flatMap(path -> {

                ZeroCopyHttpOutputMessage zeroCopyResponse = (ZeroCopyHttpOutputMessage) response;
                HttpHeaders headers = response.getHeaders();
                headers.add(HttpHeaders.CONTENT_TYPE, "application/zip");
                headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + request.getAppCode() + "_"
                        + request.getClientCode() + "." + this.getExtension());

                File file = path.toFile();
                return zeroCopyResponse.writeWith(file, 0, file.length()).doFinally(e -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ex) {
                        throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
                    }
                });

            }).subscribeOn(Schedulers.boundedElastic());
        }

        return this.makeJSONTransport(request).flatMap(transport -> {

            ZeroCopyHttpOutputMessage zeroCopyResponse = (ZeroCopyHttpOutputMessage) response;
            HttpHeaders headers = response.getHeaders();
            headers.add(HttpHeaders.CONTENT_TYPE, "application/json");

            byte[] bytes = null;
            try {
                bytes = this.objectMapper.writeValueAsBytes(transport);
            } catch (Exception ex) {
                throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
            }

            return zeroCopyResponse.writeWith(Mono.just(zeroCopyResponse.bufferFactory().wrap(bytes)));
        }).subscribeOn(Schedulers.boundedElastic());

    }

    @SneakyThrows
    private Mono<Path> makeZipTransport(TransportRequest request) {

        Transport to = new Transport();

        to.setAppCode(request.getAppCode());
        to.setClientCode(request.getClientCode());
        to.setName(request.getName());
        to.setUniqueTransportCode(UniqueUtil.shortUUID());
        to.setType(this.getTransportType());

        Path tempDir = Files.createTempDirectory("transport");
        Path path = tempDir.resolve(to.getUniqueTransportCode() + ".zip");
        FileSystem zipfs = FileSystems.newFileSystem(URI.create("jar:" + path.toUri().toString()),
                Map.of("create", "true"));

        Files.write(zipfs.getPath("/transport.json"), this.objectMapper.writeValueAsBytes(to));

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.accessCheck(ca, CREATE, request.getAppCode(), request.getClientCode(), false),

                (ca, hasPermission) -> {

                    if (!hasPermission.booleanValue()) {
                        return this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                AbstractMongoMessageResourceService.FORBIDDEN_CREATE, TRANSPORT);
                    }

                    return Flux.fromIterable(this.getServieMap())
                            .flatMap(service -> {
                                List<String> list = request.getObjectList() == null ? null
                                        : request.getObjectList()
                                                .get(service.getObjectName());
                                if (request.getObjectList() != null && !request.getObjectList()
                                        .isEmpty() && list == null)
                                    return Flux.empty();

                                Path dirPath = zipfs.getPath("/", service.getObjectName());
                                try {
                                    Files.createDirectories(dirPath);
                                } catch (Exception ex) {
                                    throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
                                }

                                return writeObjectsToDirectory(request, service, list, dirPath);
                            })
                            .collectList();

                })
                .map(e -> {

                    try {
                        zipfs.close();
                        return path;
                    } catch (Exception ex) {
                        throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
                    }
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractTransportService.makeZipTransport"));

    }

    @SuppressWarnings({ "rawtypes" })
    private Flux<Boolean> writeObjectsToDirectory(TransportRequest request,
            AbstractOverridableDataService service,
            List<String> list, Path dirPath) {
        return ((AbstractOverridableDataService<?, ?>) service)
                .readForTransport(request.getAppCode(), request.getClientCode(), list)
                .map(e -> {
                    try {
                        Files.write(dirPath.resolve(e.getName() + ".json"), this.objectMapper
                                .writeValueAsBytes(e));
                    } catch (Exception ex) {
                        throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR,
                                ex.getMessage(), ex);
                    }

                    return true;
                });
    }

    private Mono<Transport> makeJSONTransport(TransportRequest request) {
        Transport to = new Transport();

        to.setAppCode(request.getAppCode());
        to.setClientCode(request.getClientCode());
        to.setName(request.getName());
        to.setUniqueTransportCode(UniqueUtil.shortUUID());
        to.setType(this.getTransportType());

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.accessCheck(ca, CREATE, request.getAppCode(), request.getClientCode(), false),

                (ca, hasPermission) -> {

                    if (!hasPermission.booleanValue()) {
                        return this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                AbstractMongoMessageResourceService.FORBIDDEN_CREATE, TRANSPORT);
                    }

                    return Flux.fromIterable(this.getServieMap())
                            .flatMap(e -> {
                                List<String> list = request.getObjectList() == null ? null
                                        : request.getObjectList()
                                                .get(e.getObjectName());
                                if (request.getObjectList() != null && !request.getObjectList()
                                        .isEmpty() && list == null)
                                    return Flux.empty();

                                return ((AbstractOverridableDataService<?, ?>) e)
                                        .readForTransport(request.getAppCode(), request.getClientCode(), list)
                                        .map(e::makeTransportObject);
                            })
                            .collectList()
                            .map(to::setObjects);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractTransportService.makeJSONTransport"));
    }

    @SuppressWarnings("rawtypes")
    public abstract List<AbstractOverridableDataService> getServieMap();

    protected abstract String getTransportType();

    protected abstract String getExtension();
}
