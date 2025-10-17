package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.dao.ProductCommDAO;
import com.fincity.saas.entity.processor.dto.ProductComm;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductCommsRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.ProductCommRequest;
import com.fincity.saas.entity.processor.oserver.core.document.Connection;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionSubType;
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

        if (entity.getProductId() != null)
            return super.cacheService.evict(
                    this.getCacheName(),
                    super.getCacheKey(
                            entity.getAppCode(),
                            entity.getClientCode(),
                            entity.getConnectionName(),
                            entity.getConnectionType()));

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
        if (entity.getProductId() == null) {
            entity.setDefault(true);
            entity.setSource(null);
            entity.setSubSource(null);
        }

        if (StringUtil.safeIsBlank(entity.getConnectionType()))
            return super.throwMissingParam(ProductComm.Fields.connectionType);

        ConnectionType connectionType = ConnectionType.valueOf(entity.getConnectionType());
        ConnectionSubType connectionSubType = ConnectionSubType.valueOf(entity.getConnectionSubType());

        return FlatMapUtil.flatMapMono(
                () -> this.validateByType(entity, access, connectionType, connectionSubType), validated -> {
                    if (entity.isDefault()) return checkDefault(entity, access, connectionType, connectionSubType);

                    if (StringUtil.safeIsBlank(entity.getSource()))
                        return super.throwMissingParam(ProductComm.Fields.source);

                    return this.checkDuplicate(
                            this.dao.getProductComm(
                                    access,
                                    entity.getProductId(),
                                    connectionType,
                                    connectionSubType,
                                    entity.getSource(),
                                    entity.getSubSource()),
                            entity,
                            access);
                });
    }

    private Mono<ProductComm> validateByType(
            ProductComm entity,
            ProcessorAccess access,
            ConnectionType connectionType,
            ConnectionSubType connectionSubType) {
        return switch (connectionType) {
            case MAIL ->
                this.validateMail(entity)
                        .flatMap(valid -> this.checkDuplicate(
                                this.dao.getProductComm(access, connectionType, connectionSubType, entity.getEmail()),
                                entity,
                                access));
            case CALL, TEXT ->
                this.validatePhone(entity)
                        .flatMap(valid -> this.checkDuplicate(
                                this.dao.getProductComm(
                                        access,
                                        connectionType,
                                        connectionSubType,
                                        entity.getDialCode(),
                                        entity.getPhoneNumber()),
                                entity,
                                access));
            default -> Mono.just(entity);
        };
    }

    private Mono<ProductComm> checkDefault(
            ProductComm entity,
            ProcessorAccess access,
            ConnectionType connectionType,
            ConnectionSubType connectionSubType) {
        if (!entity.isDefault()) return super.throwMissingParam(ProductComm.Fields.isDefault);

        if (entity.getSource() != null || entity.getSubSource() != null)
            return super.throwInvalidParam(ProductComm.Fields.source);

        if (entity.getId() != null) {
            return FlatMapUtil.flatMapMono(
                            () -> this.getDefault(access, entity.getProductId(), connectionType, connectionSubType),
                            defaultComm -> {
                                if (!defaultComm.getId().equals(entity.getId()))
                                    return super.throwInvalidParam(ProductComm.Fields.isDefault);
                                return Mono.just(entity);
                            })
                    .switchIfEmpty(Mono.just(entity));
        }
        return Mono.just(entity);
    }

    private Mono<ProductComm> validateMail(ProductComm entity) {
        return StringUtil.safeIsBlank(entity.getEmail())
                ? super.throwMissingParam(ProductComm.Fields.email)
                : Mono.just(entity);
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

        return FlatMapUtil.flatMapMono(super::hasAccess, access -> {
                    Mono<Connection> connMono = this.connectionServices
                            .getService(productCommRequest.getConnectionType())
                            .getCoreDocument(
                                    access.getAppCode(),
                                    access.getClientCode(),
                                    productCommRequest.getConnectionName());

                    if (productCommRequest.getProductId() != null
                            && !productCommRequest.getProductId().isNull()) {
                        return this.productService
                                .readIdentityWithAccess(access, productCommRequest.getProductId())
                                .flatMap(product -> connMono.flatMap(connection -> {
                                    ULong prodId = product.getId();
                                    if (Boolean.TRUE.equals(productCommRequest.isDefault())) {
                                        return this.updateDefault(access, connection, productCommRequest)
                                                .switchIfEmpty(super.create(
                                                        access,
                                                        ProductComm.of(productCommRequest, prodId, connection)));
                                    }
                                    return super.create(
                                            access, ProductComm.of(productCommRequest, prodId, connection));
                                }));
                    }
                    return connMono.flatMap(connection -> {
                        ULong prodId = null;
                        return this.updateDefault(access, connection, productCommRequest)
                                .switchIfEmpty(super.create(
                                        access, ProductComm.of(productCommRequest, prodId, connection)));
                    });
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductCommService.updatableEntity"));
    }

    public Mono<ProductComm> updateDefault(
            ProcessorAccess access, Connection connection, ProductCommRequest productCommRequest) {

        ULong prodId = productCommRequest.getProductId() == null
                ? null
                : productCommRequest.getProductId().getULongId();

        return FlatMapUtil.flatMapMono(
                        () -> prodId != null
                                ? this.getDefault(
                                        access,
                                        prodId,
                                        connection.getConnectionType(),
                                        connection.getConnectionSubType())
                                : this.getAppDefault(
                                        access, connection.getConnectionType(), connection.getConnectionSubType()),
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
            ConnectionType connectionType,
            ConnectionSubType connectionSubType,
            String source,
            String subSource) {
        return this.getProductCommInternal(access, productId, connectionType, connectionSubType, source, subSource)
                .switchIfEmpty(this.getDefault(access, productId, connectionType, connectionSubType))
                .switchIfEmpty(this.getAppDefault(access, connectionType, connectionSubType))
                .switchIfEmpty(Mono.empty())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductCommService.getProductComm"));
    }

    public Mono<ProductComm> getDefault(
            Identity productId, ConnectionType connectionType, ConnectionSubType connectionSubType) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.getDefault(access, productId, connectionType, connectionSubType))
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME, "ProductCommService.getDefault[Identity, String, ConnectionType]"));
    }

    public Mono<ProductComm> getDefault(
            ProcessorAccess access,
            Identity productId,
            ConnectionType connectionType,
            ConnectionSubType connectionSubType) {
        return this.getDefaultInternal(access, productId.getULongId(), connectionType, connectionSubType)
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME,
                        "ProductCommService.getDefault[ProcessorAccess, Identity, String, ConnectionType]"));
    }

    public Mono<ProductComm> getDefault(
            ProcessorAccess access,
            ULong productId,
            ConnectionType connectionType,
            ConnectionSubType connectionSubType) {
        return this.getDefaultInternal(access, productId, connectionType, connectionSubType)
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME,
                        "ProductCommService.getDefault[ProcessorAccess, ULong, String, ConnectionType]"));
    }

    public Mono<ProductComm> getAppDefault(
            ProcessorAccess access, ConnectionType connectionType, ConnectionSubType connectionSubType) {
        return this.getDefaultAppInternal(access, connectionType, connectionSubType)
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME,
                        "ProductCommService.getDefault[ProcessorAccess, ULong, String, ConnectionType]"));
    }

    public Mono<ProductComm> getByPhoneNumber(
            ProcessorAccess access,
            ConnectionType connectionType,
            ConnectionSubType connectionSubType,
            PhoneNumber phoneNumber) {
        return this.dao.getProductComm(
                access, connectionType, connectionSubType, phoneNumber.getCountryCode(), phoneNumber.getNumber());
    }

    private Mono<ProductComm> getProductCommInternal(
            ProcessorAccess access,
            ULong productId,
            ConnectionType connectionType,
            ConnectionSubType connectionSubType,
            String source,
            String subSource) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.getProductComm(access, productId, connectionType, connectionSubType, source, subSource),
                super.getCacheKey(
                        access.getAppCode(),
                        access.getClientCode(),
                        productId,
                        connectionType.name(),
                        connectionSubType.name(),
                        source,
                        subSource));
    }

    private Mono<ProductComm> getDefaultAppInternal(
            ProcessorAccess access, ConnectionType connectionType, ConnectionSubType connectionSubType) {

        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.getDefaultAppProductComm(access, connectionType, connectionSubType),
                super.getCacheKey(
                        access.getAppCode(), access.getClientCode(), connectionType.name(), connectionSubType.name()));
    }

    private Mono<ProductComm> getDefaultInternal(
            ProcessorAccess access,
            ULong productId,
            ConnectionType connectionType,
            ConnectionSubType connectionSubType) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.getDefaultProductComm(access, productId, connectionType, connectionSubType),
                super.getCacheKey(
                        access.getAppCode(),
                        access.getClientCode(),
                        productId,
                        connectionType.name(),
                        connectionSubType.name()));
    }
}
