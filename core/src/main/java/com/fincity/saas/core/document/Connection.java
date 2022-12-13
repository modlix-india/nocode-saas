package com.fincity.saas.core.document;

import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "connectionFilteringIndex")
@Accessors(chain = true)
@NoArgsConstructor
public class Connection extends AbstractOverridableDTO<Connection> {

	private static final long serialVersionUID = -5507743337705010640L;

	private String connectionType;
	private Map<String, Object> connectionDetails; // NOSONAR
	private Integer order;
	private Boolean defaultConnection = false;

	@Override
	public Mono<Connection> applyOverride(Connection base) {
		return Mono.just(this);
	}

	@Override
	public Mono<Connection> makeOverride(Connection base) {
		return Mono.just(this);
	}
}
