package com.fincity.saas.ui.model;

import java.io.Serializable;
import java.util.Map;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.mongo.difference.IDifferentiable;
import com.fincity.saas.commons.mongo.util.CloneUtil;
import com.fincity.saas.commons.mongo.util.DifferenceApplicator;
import com.fincity.saas.commons.mongo.util.DifferenceExtractor;
import com.fincity.saas.commons.util.LogUtil;

import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Data
@NoArgsConstructor
public class KIRunFxDefinition implements Serializable, IDifferentiable<KIRunFxDefinition> {

	private String name;
	private String namespace;
	private String functionAppCode;

	private Map<String, String> headersMapping;
	private Map<String, String> pathParamMapping;
	private Map<String, String> queryParamMapping;

	private String outputEventName;
	private String outputEventParamName;

	public KIRunFxDefinition(KIRunFxDefinition kinRunFxDefinition) {
		this.name = kinRunFxDefinition.name;
		this.namespace = kinRunFxDefinition.namespace;
		this.functionAppCode = kinRunFxDefinition.functionAppCode;
		this.headersMapping = CloneUtil.cloneMapObject(kinRunFxDefinition.headersMapping);
		this.pathParamMapping = CloneUtil.cloneMapObject(kinRunFxDefinition.pathParamMapping);
		this.queryParamMapping = CloneUtil.cloneMapObject(kinRunFxDefinition.queryParamMapping);
		this.outputEventName = kinRunFxDefinition.outputEventName;
		this.outputEventParamName = kinRunFxDefinition.outputEventParamName;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<KIRunFxDefinition> extractDifference(KIRunFxDefinition inc) {
		if (inc == null)
			return Mono.just(this);

		return FlatMapUtil.flatMapMono(
				() -> DifferenceExtractor.extract(this.headersMapping, inc.headersMapping),
				hm -> DifferenceExtractor.extract(this.pathParamMapping, inc.pathParamMapping),
				(hm, pp) -> DifferenceExtractor.extract(this.queryParamMapping, inc.queryParamMapping),
				(hm, pp, qp) -> {
					KIRunFxDefinition diff = new KIRunFxDefinition();
					diff.setHeadersMapping((Map<String, String>) hm);
					diff.setPathParamMapping((Map<String, String>) pp);
					diff.setQueryParamMapping((Map<String, String>) qp);

					if (!this.name.equals(inc.name))
						diff.setName(this.name);
					if (!this.namespace.equals(inc.namespace))
						diff.setNamespace(this.namespace);
					if (!this.functionAppCode.equals(inc.functionAppCode))
						diff.setFunctionAppCode(this.functionAppCode);
					if (!this.outputEventName.equals(inc.outputEventName))
						diff.setOutputEventName(this.outputEventName);
					if (!this.outputEventParamName.equals(inc.outputEventParamName))
						diff.setOutputEventParamName(this.outputEventParamName);

					return Mono.just(diff);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "KIRunFxDefinition.extractDifference"));
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<KIRunFxDefinition> applyOverride(KIRunFxDefinition override) {
		if (override == null)
			return Mono.just(this);

		return FlatMapUtil.flatMapMono(
				() -> DifferenceApplicator.apply(this.headersMapping, override.headersMapping),
				hm -> DifferenceApplicator.apply(this.pathParamMapping, override.pathParamMapping),
				(hm, pp) -> DifferenceApplicator.apply(this.queryParamMapping, override.queryParamMapping),
				(hm, pp, qp) -> {
					this.setHeadersMapping((Map<String, String>) hm);
					this.setPathParamMapping((Map<String, String>) pp);
					this.setQueryParamMapping((Map<String, String>) qp);

					if (override.getName() != null)
						this.setName(override.getName());
					if (override.getNamespace() != null)
						this.setNamespace(override.getNamespace());
					if (override.getFunctionAppCode() != null)
						this.setFunctionAppCode(override.getFunctionAppCode());
					if (override.getOutputEventName() != null)
						this.setOutputEventName(override.getOutputEventName());
					if (override.getOutputEventParamName() != null)
						this.setOutputEventParamName(override.getOutputEventParamName());

					return Mono.just(this);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "KIRunFxDefinition.applyOverride"));
	}
}
