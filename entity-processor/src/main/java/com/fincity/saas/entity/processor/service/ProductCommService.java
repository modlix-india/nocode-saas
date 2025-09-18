package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.dao.ProductCommDAO;
import com.fincity.saas.entity.processor.dto.ProductComm;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductCommsRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.ProductCommRequest;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionType;
import com.fincity.saas.entity.processor.oserver.core.service.ConnectionServiceProvider;
import com.fincity.saas.entity.processor.service.base.BaseProcessorService;
import java.util.Map;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

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
    protected Mono<Boolean> evictCache(ProductComm entity) {
        return Mono.zip(
                super.evictCache(entity),
                super.cacheService.evict(
                        this.getCacheName(),
                        super.getCacheKey(
                                entity.getAppCode(),
                                entity.getClientCode(),
                                entity.getProductId(),
                                entity.getConnectionName(),
                                entity.getConnectionType())),
                (baseEvicted, defaultEvicted) -> baseEvicted && defaultEvicted);
    }

    @Override
    protected Mono<ProductComm> checkEntity(ProductComm entity, ProcessorAccess access) {

        if (entity.getProductId() == null) return super.throwMissingParam(ProductComm.Fields.productId);
        if (StringUtil.safeIsBlank(entity.getConnectionType()))
            return super.throwMissingParam(ProductComm.Fields.connectionType);

        if (!entity.isDefault() && StringUtil.safeIsBlank(entity.getSource()))
            return super.throwMissingParam(ProductComm.Fields.source);

        ConnectionType connectionType = ConnectionType.valueOf(entity.getConnectionType());

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
        return super.updatableEntity(entity).flatMap(existing -> {
            existing.setDialCode(entity.getDialCode());
            existing.setPhoneNumber(entity.getPhoneNumber());
            existing.setEmail(entity.getEmail());
            existing.setDefault(entity.isDefault());
            existing.setSource(entity.getSource());
            existing.setSubSource(entity.getSubSource());

            return Mono.just(existing);
        });
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
                                access.getAppCode(), access.getClientCode(), productCommRequest.getConnectionName()),
                (access, product, connection) -> {
                    if (Boolean.TRUE.equals(productCommRequest.isDefault()))
                        return this.dao
                                .unsetDefaultsForProduct(
                                        access,
                                        productCommRequest.getProductId().getULongId(),
                                        productCommRequest.getConnectionType())
                                .then(super.createInternal(
                                        access, ProductComm.of(productCommRequest, product.getId(), connection)));
                    return super.createInternal(
                            access, ProductComm.of(productCommRequest, product.getId(), connection));
                });
    }

    public Mono<ProductComm> setDefaultNumber(Identity productCommId) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess, access -> this.readIdentityWithAccess(access, productCommId), (access, pc) -> this.dao
                        .unsetDefaultsForProduct(
                                access, pc.getProductId(), ConnectionType.valueOf(pc.getConnectionType()))
                        .then(super.updateInternal(
                                access, pc.getId(), Map.of(ProductComm.Fields.isDefault, Boolean.TRUE))));
    }

    public Mono<ProductComm> getDefault(Identity productId, String connectionName, ConnectionType connectionType) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess, access -> this.getDefault(access, productId, connectionName, connectionType));
    }

    public Mono<ProductComm> getDefault(
            ProcessorAccess access, Identity productId, String connectionName, ConnectionType connectionType) {
        return this.getDefaultInternal(access, productId.getULongId(), connectionName, connectionType);
    }

    public Mono<ProductComm> getDefault(
            ProcessorAccess access, ULong productId, String connectionName, ConnectionType connectionType) {
        return this.getDefaultInternal(access, productId, connectionName, connectionType);
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
