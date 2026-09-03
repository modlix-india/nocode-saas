package com.fincity.saas.commons.mongo.service;

import java.io.File;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ZeroCopyHttpOutputMessage;
import org.springframework.http.server.reactive.ServerHttpResponse;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.dto.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.document.Transport;
import com.fincity.saas.commons.mongo.enums.TransportFileType;
import com.fincity.saas.commons.mongo.model.TransportObject;
import com.fincity.saas.commons.mongo.model.TransportRequest;
import com.fincity.saas.commons.mongo.repository.TransportRepository;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.commons.util.TopologicalUtil;
import com.fincity.saas.commons.util.UniqueUtil;

import lombok.SneakyThrows;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;

public abstract class AbstractTransportService extends AbstractOverridableDataService<Transport, TransportRepository> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractTransportService.class);

    private static final String TRANSPORT = "Transport";
    private static final String SLASH_ESCAPE = "__slash__";

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

    @SuppressWarnings({ "unchecked" })
    private Mono<Boolean> applyTransportForZip(String transportForAppCode, boolean isBaseApp, ContextAuthentication ca,
            Transport transport, Optional<Tuple2<String, Boolean>> baseCodeTup) {

        try {
            Path tempDir = Files.createTempDirectory("transport");
            Path zipFile = tempDir.resolve(transport.getUniqueTransportCode() + ".zip");

            Files.write(zipFile, Base64.getDecoder().decode(transport.getEncodedModl()));

            FileSystem zipfs = FileSystems.newFileSystem(URI.create("jar:" + zipFile.toUri().toString()), Map.of());

            // Read the whole zip up front rather than streaming inside the
            // apply. Everything in it is held in memory either way, and the
            // objects have to be grouped by type before any of them is saved
            // so they can be ordered.
            Map<String, List<TransportObject>> byType = new HashMap<>();

            for (AbstractOverridableDataService<?, ?> service : this.getServieMap()) {

                Path dirPath = zipfs.getPath("/", service.getObjectName());
                if (!Files.exists(dirPath))
                    continue;

                List<TransportObject> objects = new ArrayList<>();

                try (var paths = Files.list(dirPath)) {
                    for (Path file : paths.filter(e -> e.toString().toLowerCase().trim().endsWith(".json"))
                            .toList()) {
                        Map<String, Object> data = this.objectMapper.readValue(Files.readString(file), Map.class);
                        objects.add(new TransportObject().setData(data).setObjectType(service.getObjectName()));
                    }
                }

                if (!objects.isEmpty())
                    byType.put(service.getObjectName(), objects);
            }

            return this.applyObjects(byType, transportForAppCode, ca, baseCodeTup, transport, isBaseApp)
                    .doFinally(e -> {
                        try {
                            zipfs.close();
                            Files.deleteIfExists(zipFile);
                            Files.deleteIfExists(tempDir);
                        } catch (Exception ex) {
                            // A leftover temp file is not worth failing an
                            // import that already succeeded, and throwing from
                            // doFinally would swallow the real outcome.
                            logger.warn("Could not clean up the transport temp files under {}", tempDir, ex);
                        }
                    })
                    // Reading the zip and every save below are blocking, and
                    // applyTransport has no scheduler of its own the way
                    // makeTransport does.
                    .subscribeOn(Schedulers.boundedElastic());
        } catch (Exception ex) {
            throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }

    private Mono<Boolean> applyTransportForJSON(String transportForAppCode, boolean isBaseApp, ContextAuthentication ca,
            Transport transport, Optional<Tuple2<String, Boolean>> baseCodeTup) {

        Map<String, List<TransportObject>> byType = transport.getObjects()
                .stream()
                .filter(e -> e.getObjectType() != null)
                .collect(Collectors.groupingBy(TransportObject::getObjectType));

        return this.applyObjects(byType, transportForAppCode, ca, baseCodeTup, transport, isBaseApp);
    }

    /**
     * Saves everything in a transport in an order its dependencies survive.
     * <p>
     * Two things are ordered here. Object types go one after another in
     * {@code getServieMap()} order, because saving one type can resolve
     * references into another - a Storage's schema ref has to find its Schema,
     * and both are in the same transport. Within a type, objects are grouped
     * into dependency waves, so a Storage whose relation points at another
     * Storage in the same transport is saved after it. Objects inside a wave
     * are independent of each other and still go concurrently.
     */
    private Mono<Boolean> applyObjects(Map<String, List<TransportObject>> byType,
            String transportForAppCode, ContextAuthentication ca, Optional<Tuple2<String, Boolean>> baseCodeTup,
            Transport transport, boolean isBaseApp) {

        return Flux.fromIterable(this.getServieMap())
                .concatMap(service -> {

                    List<TransportObject> objects = byType.get(service.getObjectName());
                    if (objects == null || objects.isEmpty())
                        return Flux.<Boolean>empty();

                    return this.applyObjectsOfOneType(service, objects, transportForAppCode, ca, baseCodeTup,
                            transport, isBaseApp);
                })
                .then(Mono.just(true));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Flux<Boolean> applyObjectsOfOneType(AbstractOverridableDataService service,
            List<TransportObject> objects,
            String transportForAppCode, ContextAuthentication ca, Optional<Tuple2<String, Boolean>> baseCodeTup,
            Transport transport, boolean isBaseApp) {

        List<AbstractOverridableDTO> entities = objects.stream()
                .map(o -> (AbstractOverridableDTO) service.makeEntity(o.getObjectType(), o.getData()))
                .filter(Objects::nonNull)
                .toList();

        TopologicalUtil.Ordered<AbstractOverridableDTO> ordered = TopologicalUtil.sort(entities,
                AbstractOverridableDTO::getName, service::getTransportDependencies);

        return Flux.fromIterable(ordered.waves())
                .concatMap(wave -> Flux.fromIterable(wave)
                        .flatMap(e -> this.addObjectToService(service, e, transportForAppCode, ca, baseCodeTup,
                                transport, isBaseApp))
                        .collectList())
                .thenMany(this.applyCyclicObjects(service, ordered.cyclic(), transportForAppCode, ca, baseCodeTup,
                        transport, isBaseApp));
    }

    /**
     * Saves the objects a dependency cycle left behind, in two passes.
     * <p>
     * Objects that reference each other in a cycle cannot be saved in any
     * single order when the save itself resolves those references. Each one
     * goes in first with its links stripped, then again whole once all of them
     * exist. The second pass finds the row from the first and takes the update
     * branch, so the end state is the one a single pass would have reached.
     * <p>
     * A type that cannot strip its links returns null and the first pass does
     * nothing for it, which leaves the second pass saving them as they are -
     * the best that can be done, and what used to happen to everything.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Flux<Boolean> applyCyclicObjects(AbstractOverridableDataService service,
            List<AbstractOverridableDTO> cyclic,
            String transportForAppCode, ContextAuthentication ca, Optional<Tuple2<String, Boolean>> baseCodeTup,
            Transport transport, boolean isBaseApp) {

        if (cyclic.isEmpty())
            return Flux.empty();

        logger.warn("{} objects of type {} depend on each other in a cycle, saving them in two passes : {}",
                cyclic.size(), service.getObjectName(),
                cyclic.stream().map(AbstractOverridableDTO::getName).toList());

        return Flux.fromIterable(cyclic)
                .concatMap(e -> {
                    AbstractOverridableDTO stripped = (AbstractOverridableDTO) service.stripTransportDependencies(e);
                    return stripped == null ? Mono.empty()
                            : this.addObjectToService(service, stripped, transportForAppCode, ca, baseCodeTup,
                                    transport, isBaseApp);
                })
                .collectList()
                .flatMapMany(e -> Flux.fromIterable(cyclic)
                        .concatMap(entity -> this.addObjectToService(service, entity, transportForAppCode, ca,
                                baseCodeTup, transport, isBaseApp)));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Mono<Boolean> addObjectToService(
            AbstractOverridableDataService service,
            AbstractOverridableDTO tentity,
            String transportForAppCode, ContextAuthentication ca, Optional<Tuple2<String, Boolean>> baseCodeTup,
            Transport transport, boolean isBaseApp) {

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

        return FlatMapUtil.flatMapMonoWithNull(

                () -> service.readToTransport(tentity.getName(), tentity.getAppCode(), tentity.getClientCode()),

                tup -> {
                    if (tup == null)
                        return service.create(tentity);

                    tentity.setVersion(((Tuple2<Integer, String>) tup).getT1());
                    tentity.setId(((Tuple2<Integer, String>) tup).getT2());
                    return service.update(tentity);
                },

                (tup, entity) -> Mono.just(true))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractTransportService.addObjectToService"));
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

    public Mono<Void> makeTransport(boolean isInternal, TransportRequest request, ServerHttpResponse response) {

        if (request.getFileType() == null || request.getFileType() == TransportFileType.ZIP) {

            return this.makeZipTransport(isInternal, request).flatMap(path -> {

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

        return this.makeJSONTransport(isInternal, request).flatMap(transport -> {

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
    private Mono<Path> makeZipTransport(boolean isInternal, TransportRequest request) {

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

                ca -> isInternal ? Mono.just(true)
                        : this.accessCheck(ca, CREATE, request.getAppCode(), request.getClientCode(), false),

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
                        Files.write(dirPath.resolve(toFileName(e.getTransportName())), this.objectMapper
                                .writeValueAsBytes(e));
                    } catch (Exception ex) {
                        throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR,
                                ex.getMessage(), ex);
                    }

                    return true;
                });
    }

    /**
     * Turns an object's name into a file name for the transport zip.
     * <p>
     * Some names are routes: a URIPath is named things like
     * {@code /api/exotel/call}. Handed to {@code dirPath.resolve()} that reads
     * as an absolute path, which drops the type folder and tries to write
     * outside it onto parents that do not exist. Escaping the separator keeps
     * the file name derived from the name, so it stays reversible, it is the
     * same in every environment, and it can be handed straight back to an
     * objectList. Same idea as the {@code __d-o-t__} replacement already used
     * for Mongo map keys.
     */
    private static String toFileName(String transportName) {
        return StringUtil.safeValueOf(transportName, "").replace("/", SLASH_ESCAPE) + ".json";
    }

    private Mono<Transport> makeJSONTransport(boolean isInternal, TransportRequest request) {
        Transport to = new Transport();

        to.setAppCode(request.getAppCode());
        to.setClientCode(request.getClientCode());
        to.setName(request.getName());
        to.setUniqueTransportCode(UniqueUtil.shortUUID());
        to.setType(this.getTransportType());

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> isInternal ? Mono.just(ca.isSystemClient())
                        : this.accessCheck(ca, CREATE, request.getAppCode(), request.getClientCode(), false),

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

    public abstract List<AbstractOverridableDataService<?, ?>> getServieMap(); // NOSONAR
    // This is a generic service and requires wildcard

    protected abstract String getTransportType();

    protected abstract String getExtension();
}
