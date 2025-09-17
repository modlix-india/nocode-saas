package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.processor.dao.ProductCommDAO;
import com.fincity.saas.entity.processor.dto.ProductComm;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductCommsRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.ProductCommRequest;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionType;
import com.fincity.saas.entity.processor.oserver.core.service.ConnectionServiceProvider;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ProductCommService
        extends BaseUpdatableService<EntityProcessorProductCommsRecord, ProductComm, ProductCommDAO> {

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
    protected Mono<ProductComm> updatableEntity(ProductComm entity) {
        return super.updatableEntity(entity).flatMap(existing -> {
            existing.setDialCode(entity.getDialCode());
            existing.setPhoneNumber(entity.getPhoneNumber());
            existing.setEmail(entity.getEmail());
            existing.setDefault(entity.isDefault());

            return Mono.just(existing);
        });
    }

    public Mono<ProductComm> create(ProductCommRequest productCommRequest) {

        if (productCommRequest.getConnectionType() == null)
            return super.throwMissingParam(ProductComm.Fields.connectionType);

        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> productService.readIdentityWithAccess(access, productCommRequest.getProductId()),
                (access, product) -> this.connectionServices
                        .getService(productCommRequest.getConnectionType())
                        .getCoreDocument(
                                access.getAppCode(), access.getClientCode(), productCommRequest.getConnectionName()),
                (access, product, connection) -> {
                    if (Boolean.TRUE.equals(productCommRequest.getIsDefault()))
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
                        .then(this.updateInternal(
                                access, pc.getId(), Map.of(ProductComm.Fields.isDefault, Boolean.TRUE))));
    }
}
