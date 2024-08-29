package com.fincity.saas.ui.model;

import java.io.Serializable;

import com.fincity.saas.commons.mongo.difference.IDifferentiable;
import com.fincity.saas.commons.util.EqualsUtil;
import com.fincity.saas.commons.util.LogUtil;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KIRunFxDefinition implements Serializable, IDifferentiable<KIRunFxDefinition> {

	private String name;
	private String namespace;

	private boolean override;

	@Override
	public Mono<KIRunFxDefinition> extractDifference(KIRunFxDefinition incoming) {

		KIRunFxDefinition diff = new KIRunFxDefinition();

		if (!EqualsUtil.safeEquals(this.name, incoming.name)) {
			diff.setName(this.name);
		}

		if (!EqualsUtil.safeEquals(this.namespace, incoming.namespace)) {
			diff.setNamespace(this.namespace);
		}

		return Mono.just(diff).contextWrite(Context.of(LogUtil.METHOD_NAME, "KIRunFxDefinition.extractDifference"));
	}

	@Override
	public Mono<KIRunFxDefinition> applyOverride(KIRunFxDefinition override) {

		if (override == null) {
			return this.isOverride() ? Mono.empty() : Mono.justOrEmpty(this);
		}

		KIRunFxDefinition result = new KIRunFxDefinition();

		result.setName(this.name != null ? this.name : override.name);
		result.setNamespace(this.namespace != null ? this.namespace : override.namespace);
		result.setOverride(true);

		return Mono.just(result).contextWrite(Context.of(LogUtil.METHOD_NAME, "KIRunFxDefinition.applyOverride"));
	}
}
