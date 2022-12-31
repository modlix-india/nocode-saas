package com.fincity.saas.core.document;

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
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "actionFilteringIndex")
@Accessors(chain = true)
@NoArgsConstructor
public class Action extends AbstractOverridableDTO<Action> {

	private static final long serialVersionUID = 3425030507970576753L;

	private String functionNamespace;
	private String functionName;

	public Action(Action action) {
		
		super(action);
		this.functionName = action.functionName;
		this.functionNamespace = action.functionNamespace;
	}

	@Override
	public Mono<Action> applyOverride(Action base) {
		return Mono.just(this);
	}

	@Override
	public Mono<Action> makeOverride(Action base) {
		return Mono.just(this);
	}
}
