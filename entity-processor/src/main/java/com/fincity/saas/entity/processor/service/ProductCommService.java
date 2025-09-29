package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.dao.ProductCommDAO;
import com.fincity.saas.entity.processor.dto.ProductComm;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductCommsRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.ProductCommRequest;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionType;
import com.fincity.saas.entity.processor.oserver.core.service.ConnectionServiceProvider;
import com.fincity.saas.entity.processor.service.base.BaseProcessorService;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ProductCommService
        extends BaseProcessorService<EntityProcessorProductCommsRecord, ProductComm, ProductCommDAO> {

    private static final String PRODUCT_COMM = "productComm";

    private final ConnectionServiceProvider connectionServices;
    private final ProductService productService;

    public ProductCommService(ConnectionServiceProvider connectionServices, ProductService productService) {
        this.connectionServices = connectionServices;
        this.productService = productService;
    }

    @Override
    protected String getCacheName() {
        return PRODUCT_COMM;
    }

    @Override
    protected boolean canOutsideCreate() {
        return Boolean.FALSE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_COMM;
    }

    @Override
    protected Mono<Boolean> evictCache(ProductComm entity) {
        return Mono.zip(
                super.evictCache(entity),
                this.evictProductCommCache(entity),
                (baseEvicted, defaultEvicted) -> baseEvicted && defaultEvicted);
    }

    private Mono<Boolean> evictProductCommCache(ProductComm entity) {
        return Mono.zip(
                super.cacheService.evict(
                        this.getCacheName(),
                        super.getCacheKey(
                                entity.getAppCode(),
                                entity.getClientCode(),
                                entity.getProductId(),
                                entity.getConnectionName(),
                                entity.getConnectionType(),
                                entity.getSource(),
                                entity.getSubSource())),
                this.evictDefaultCache(entity),
                (baseEvicted, defaultEvicted) -> baseEvicted && defaultEvicted);
    }

    private Mono<Boolean> evictDefaultCache(ProductComm entity) {
        return super.cacheService.evict(
                this.getCacheName(),
                super.getCacheKey(
                        entity.getAppCode(),
                        entity.getClientCode(),
                        entity.getProductId(),
                        entity.getConnectionName(),
                        entity.getConnectionType()));
    }

    @Override
    protected Mono<ProductComm> checkEntity(ProductComm entity, ProcessorAccess access) {

        if (entity.getProductId() == null) return super.throwMissingParam(ProductComm.Fields.productId);
        if (StringUtil.safeIsBlank(entity.getConnectionType()))
            return super.throwMissingParam(ProductComm.Fields.connectionType);

        ConnectionType connectionType = ConnectionType.valueOf(entity.getConnectionType());

        if (entity.isDefault()) return this.checkDefault(entity, access);

        if (StringUtil.safeIsBlank(entity.getSource())) return super.throwMissingParam(ProductComm.Fields.source);

        Mono<ProductComm> duplicateCheck = this.dao.getProductComm(
                access,
                entity.getProductId(),
                entity.getConnectionName(),
                connectionType,
                entity.getSource(),
                entity.getSubSource());

        return switch (connectionType) {
            case MAIL -> this.validateMail(entity).flatMap(valid -> checkDuplicate(duplicateCheck, entity, access));
            case CALL, TEXT ->
                this.validatePhone(entity).flatMap(valid -> checkDuplicate(duplicateCheck, entity, access));
            default -> Mono.empty();
        };
    }

    private Mono<ProductComm> checkDefault(ProductComm entity, ProcessorAccess access) {
        if (!entity.isDefault()) return super.throwMissingParam(ProductComm.Fields.isDefault);

        if (entity.getSource() != null || entity.getSubSource() != null)
            return super.throwInvalidParam(ProductComm.Fields.source);

        if (entity.getId() != null)
            return FlatMapUtil.flatMapMono(
                            () -> this.getDefault(
                                    access,
                                    entity.getProductId(),
                                    entity.getConnectionName(),
                                    ConnectionType.valueOf(entity.getConnectionType())),
                            defaultComm -> {
                                if (!defaultComm.getId().equals(entity.getId()))
                                    return super.throwInvalidParam(ProductComm.Fields.isDefault);
                                return Mono.just(entity);
                            })
                    .switchIfEmpty(Mono.just(entity));

        return Mono.just(entity);
    }

    private Mono<ProductComm> validateMail(ProductComm entity) {
        if (StringUtil.safeIsBlank(entity.getEmail())) return super.throwMissingParam(ProductComm.Fields.email);
        return Mono.just(entity);
    }

    private Mono<ProductComm> validatePhone(ProductComm entity) {
        if (entity.getDialCode() == null) return super.throwMissingParam(ProductComm.Fields.dialCode);
        if (StringUtil.safeIsBlank(entity.getPhoneNumber()))
            return super.throwMissingParam(ProductComm.Fields.phoneNumber);
        return Mono.just(entity);
    }

    private Mono<ProductComm> checkDuplicate(
            Mono<ProductComm> existingMono, ProductComm entity, ProcessorAccess access) {

        return existingMono
                .flatMap(existing -> {
                    if (existing == null) return Mono.just(entity);
                    if (entity.getId() == null || !existing.getId().equals(entity.getId()))
                        return this.throwDuplicateError(access, existing);
                    return Mono.just(entity);
                })
                .switchIfEmpty(Mono.just(entity));
    }

    @Override
    protected Mono<ProductComm> updatableEntity(ProductComm entity) {
        return super.updatableEntity(entity)
                .flatMap(existing -> {
                    existing.setDialCode(entity.getDialCode());
                    existing.setPhoneNumber(entity.getPhoneNumber());
                    existing.setEmail(entity.getEmail());

                    if (entity.isDefault()) {
                        existing.setSource(null);
                        existing.setSubSource(null);
                    } else {
                        existing.setSource(entity.getSource());
                        existing.setSubSource(entity.getSubSource());
                    }

                    return Mono.just(existing);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductCommService.updatableEntity"));
    }

    public Mono<ProductComm> create(ProductCommRequest productCommRequest) {

        if (productCommRequest.getConnectionName() == null)
            return super.throwMissingParam(ProductComm.Fields.connectionName);

        if (productCommRequest.getConnectionType() == null)
            return super.throwMissingParam(ProductComm.Fields.connectionType);

        if (!productCommRequest.isValid()) return super.throwMissingParam("Communication Medium objects");

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> productService.readIdentityWithAccess(access, productCommRequest.getProductId()),
                        (access, product) -> this.connectionServices
                                .getService(productCommRequest.getConnectionType())
                                .getCoreDocument(
                                        access.getAppCode(),
                                        access.getClientCode(),
                                        productCommRequest.getConnectionName()),
                        (access, product, connection) -> {
                            if (Boolean.TRUE.equals(productCommRequest.isDefault()))
                                return this.updateDefault(access, productCommRequest)
                                        .switchIfEmpty(super.createInternal(
                                                access,
                                                ProductComm.of(productCommRequest, product.getId(), connection)));
                            return super.createInternal(
                                    access, ProductComm.of(productCommRequest, product.getId(), connection));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductCommService.updatableEntity"));
    }

    public Mono<ProductComm> updateDefault(ProcessorAccess access, ProductCommRequest productCommRequest) {

        return FlatMapUtil.flatMapMono(
                        () -> this.getDefault(
                                access,
                                productCommRequest.getProductId().getULongId(),
                                productCommRequest.getConnectionName(),
                                productCommRequest.getConnectionType()),
                        defaultComm -> {
                            defaultComm = switch (productCommRequest.getConnectionType()) {
                                case MAIL ->
                                    defaultComm.setEmail(
                                            productCommRequest.getEmail().getAddress());
                                case TEXT, CALL ->
                                    defaultComm.setPhoneNumber(
                                            productCommRequest.getPhoneNumber().getNumber());
                                default -> defaultComm;
                            };

                            return super.updateInternal(access, defaultComm);
                        },
                        (defaultComm, updated) -> this.evictCache(updated).thenReturn(updated))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductCommService.updateDefault"));
    }

    public Mono<ProductComm> getProductComm(
            ProcessorAccess access,
            ULong productId,
            String connectionName,
            ConnectionType connectionType,
            String source,
            String subSource) {
        return this.getProductCommInternal(access, productId, connectionName, connectionType, source, subSource)
                .switchIfEmpty(this.getDefault(access, productId, connectionName, connectionType))
                .switchIfEmpty(Mono.empty())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductCommService.getProductComm"));
    }

    public Mono<ProductComm> getDefault(Identity productId, String connectionName, ConnectionType connectionType) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess, access -> this.getDefault(access, productId, connectionName, connectionType))
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME, "ProductCommService.getDefault[Identity, String, ConnectionType]"));
    }

    public Mono<ProductComm> getDefault(
            ProcessorAccess access, Identity productId, String connectionName, ConnectionType connectionType) {
        return this.getDefaultInternal(access, productId.getULongId(), connectionName, connectionType)
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME,
                        "ProductCommService.getDefault[ProcessorAccess, Identity, String, ConnectionType]"));
    }

    public Mono<ProductComm> getDefault(
            ProcessorAccess access, ULong productId, String connectionName, ConnectionType connectionType) {
        return this.getDefaultInternal(access, productId, connectionName, connectionType)
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME,
                        "ProductCommService.getDefault[ProcessorAccess, ULong, String, ConnectionType]"));
    }

    private Mono<ProductComm> getProductCommInternal(
            ProcessorAccess access,
            ULong productId,
            String connectionName,
            ConnectionType connectionType,
            String source,
            String subSource) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.getProductComm(access, productId, connectionName, connectionType, source, subSource),
                super.getCacheKey(
                        access.getAppCode(),
                        access.getClientCode(),
                        productId,
                        connectionName,
                        connectionType.name(),
                        source,
                        subSource));
    }

    private Mono<ProductComm> getDefaultInternal(
            ProcessorAccess access, ULong productId, String connectionName, ConnectionType connectionType) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.getDefaultProductComm(access, productId, connectionName, connectionType),
                super.getCacheKey(
                        access.getAppCode(), access.getClientCode(), productId, connectionName, connectionType.name()));
    }
}
