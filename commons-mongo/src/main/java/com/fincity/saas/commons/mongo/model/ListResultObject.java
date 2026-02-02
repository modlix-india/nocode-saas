package com.fincity.saas.commons.mongo.model;

import com.fincity.saas.commons.model.dto.AbstractOverridableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public class ListResultObject<D extends AbstractOverridableDTO<D>> extends AbstractOverridableDTO<ListResultObject<D>> {

    private static final long serialVersionUID = 4425643888630525907L;

    private D data;

    @Override
    public Mono<ListResultObject<D>> applyOverride(ListResultObject<D> base) {

        return Mono.just(base);
    }

    @Override
    public Mono<ListResultObject<D>> extractDifference(ListResultObject<D> base) {

        return Mono.just(base);
    }

}
