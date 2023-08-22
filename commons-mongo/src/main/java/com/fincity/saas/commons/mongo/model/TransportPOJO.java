package com.fincity.saas.commons.mongo.model;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class TransportPOJO extends AbstractOverridableDTO<TransportPOJO> {

	private static final long serialVersionUID = -5436810186809455453L;

	private String uniqueTransportCode;
	private List<TransportObject> objects;
	private String type;

	@Override
	public Mono<TransportPOJO> applyOverride(TransportPOJO base) {
		return Mono.just(this);
	}

	@Override
	public Mono<TransportPOJO> makeOverride(TransportPOJO base) {
		return Mono.just(this);
	}
}
