package com.fincity.saas.ui.document;

import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.saas.commons.model.dto.AbstractOverridableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'applicationName': 1, 'name': 1, 'id': 1}", name = "personalizationFilteringIndex")
@Accessors(chain = true)
@ToString(callSuper = true)
public class Personalization extends AbstractOverridableDTO<Personalization> {

	private static final long serialVersionUID = 4797291119009554778L;

	private Map<String, Object> personalization; // NOSONAR

	@Override
	public Mono<Personalization> applyOverride(Personalization base) {

		return Mono.just(this);
	}

	@Override
	public Mono<Personalization> makeOverride(Personalization base) {

		return Mono.just(this);
	}

}
