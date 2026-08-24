package com.fincity.saas.entity.processor.dao.product;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.entity.processor.dao.base.BaseProcessorDAO;
import com.fincity.saas.entity.processor.dto.product.Product;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ProductDAO extends BaseProcessorDAO<EntityProcessorProductsRecord, Product> {

    protected ProductDAO() {
        super(Product.class, ENTITY_PROCESSOR_PRODUCTS, ENTITY_PROCESSOR_PRODUCTS.ID);
    }

    @Override
    public Mono<AbstractCondition> processorAccessCondition(AbstractCondition condition, ProcessorAccess access) {

        if (!access.isOutsideUser()) return super.processorAccessCondition(condition, access);

        return super.processorAccessCondition(condition, access)
                .flatMap(con -> Mono.just(ComplexCondition.and(con, getPartnerCondition())));
    }

    private AbstractCondition getPartnerCondition() {
        return FilterCondition.make(Product.Fields.forPartner, 1);
    }

    public Mono<List<Product>> getAllProducts(ProcessorAccess access, List<ULong> productIds) {
        return FlatMapUtil.flatMapMono(
                () -> this.processorAccessCondition(
                        productIds != null
                                ? new FilterCondition()
                                        .setField(AbstractDTO.Fields.id)
                                        .setOperator(FilterConditionOperator.IN)
                                        .setMultiValue(productIds)
                                : null,
                        access),
                super::filter,
                (condition, jCondition) -> Flux.from(
                                this.dslContext.selectFrom(this.table).where(jCondition))
                        .map(rec -> rec.into(Product.class))
                        .collectList());
    }

    /**
     * Every product currently sending from one linked number.
     *
     * <p>The reverse of the mapping, which is the direction the numbers screen asks in: it lists
     * numbers and wants to show, and edit, the products on each. It is also what makes saving that
     * screen correct, since deselecting a product has to clear its code and the client has no way to
     * work out which products those were.
     *
     * <p>Not filtered on active. A deactivated product still holds its number, and clearing it here
     * because it happens to be inactive would silently drop the mapping the moment anybody edited an
     * unrelated number.
     */
    public Mono<List<Product>> readByWhatsappSessionCode(ProcessorAccess access, String sessionCode) {
        return FlatMapUtil.flatMapMono(
                () -> this.processorAccessCondition(
                        FilterCondition.make(Product.Fields.whatsappSessionCode, sessionCode)
                                .setOperator(FilterConditionOperator.EQUALS),
                        access),
                super::filter,
                (condition, jCondition) -> Flux.from(
                                this.dslContext.selectFrom(this.table).where(jCondition))
                        .map(rec -> rec.into(Product.class))
                        .collectList());
    }

    /**
     * The tenant's oldest active product.
     *
     * <p>Used when an inbound WhatsApp message arrives from a number with no deal and the business
     * number it landed on is not mapped to a product. Something has to own the deal that gets
     * created, and {@code Product} carries no ordering column, so lowest id (oldest created) is the
     * one stable choice available. A sales agent moves the deal afterwards.
     */
    public Mono<Product> readFirstActive(ProcessorAccess access) {
        return FlatMapUtil.flatMapMono(
                () -> this.processorAccessCondition(null, access),
                super::filter,
                (condition, jCondition) -> Mono.from(this.dslContext
                                .selectFrom(this.table)
                                .where(jCondition.and(super.isActiveTrue()))
                                .orderBy(ENTITY_PROCESSOR_PRODUCTS.ID.asc())
                                .limit(1))
                        .map(rec -> rec.into(Product.class)));
    }
}
