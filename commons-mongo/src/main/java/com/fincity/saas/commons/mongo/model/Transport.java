package com.fincity.saas.commons.mongo.model;

import java.util.List;

import com.fincity.saas.commons.model.dto.AbstractOverridableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class Transport extends AbstractOverridableDTO<Transport> {

    private static final long serialVersionUID = -5436810186809455453L;

    private String uniqueTransportCode;
    private List<TransportObject> objects;
    private String type;

    @Override
    public Mono<Transport> applyOverride(Transport base) {
        return Mono.just(this);
    }

    @Override
    public Mono<Transport> extractDifference(Transport base) {
        return Mono.just(this);
    }
}
