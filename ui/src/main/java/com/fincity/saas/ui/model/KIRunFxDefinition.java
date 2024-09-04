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

	private Map<String, String> headersMapping;
	private Map<String, String> pathParamMapping;
	private Map<String, String> queryParamMapping;

	public KIRunFxDefinition(KIRunFxDefinition kinRunFxDefinition) {
		this.name = kinRunFxDefinition.name;
		this.namespace = kinRunFxDefinition.namespace;
		this.headersMapping = CloneUtil.cloneMapObject(kinRunFxDefinition.headersMapping);
		this.pathParamMapping = CloneUtil.cloneMapObject(kinRunFxDefinition.pathParamMapping);
		this.queryParamMapping = CloneUtil.cloneMapObject(kinRunFxDefinition.queryParamMapping);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<KIRunFxDefinition> extractDifference(KIRunFxDefinition inc) {

		if (inc == null)
			return Mono.just(this);

		return FlatMapUtil.flatMapMono(

				() -> DifferenceExtractor.extract(inc.getHeadersMapping(), this.getHeadersMapping())
						.defaultIfEmpty(Map.of()),

				headersDiff -> DifferenceExtractor.extract(inc.getPathParamMapping(), this.getPathParamMapping())
						.defaultIfEmpty(Map.of()),

				(headersDiff, pathParamDiff) -> DifferenceExtractor.extract(inc.getQueryParamMapping(), this.getQueryParamMapping())
						.defaultIfEmpty(Map.of()),

				(headersDiff, pathParamDiff, queryParamDiff) -> {

					KIRunFxDefinition diff = new KIRunFxDefinition();

					diff.setName(inc.getName().equals(this.getName()) ? null : this.getName());
					diff.setNamespace(inc.getNamespace().equals(this.getNamespace()) ? null : this.getNamespace());

					diff.setHeadersMapping((Map<String, String>) headersDiff);
					diff.setPathParamMapping((Map<String, String>) pathParamDiff);
					diff.setQueryParamMapping((Map<String, String>) queryParamDiff);

					return Mono.just(diff);
				}
		).contextWrite(Context.of(LogUtil.METHOD_NAME, "KIRunFxDefinition.extractDifference"));
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<KIRunFxDefinition> applyOverride(KIRunFxDefinition base) {

		if (base == null)
			return Mono.just(this);

		return FlatMapUtil.flatMapMono(

				() -> DifferenceApplicator.apply(this.getHeadersMapping(), base.getHeadersMapping()),

				headersMap -> DifferenceApplicator.apply(this.getPathParamMapping(), base.getPathParamMapping()),

				(headersMap, pathParamMap) -> DifferenceApplicator.apply(this.getQueryParamMapping(), base.getQueryParamMapping()),

				(headersMap, pathParamMap, queryParamMap) -> {

					this.setHeadersMapping((Map<String, String>) headersMap);
					this.setPathParamMapping((Map<String, String>) pathParamMap);
					this.setQueryParamMapping((Map<String, String>) queryParamMap);

					if (this.getName() == null)
						this.setName(base.getName());
					if (this.getNamespace() == null)
						this.setNamespace(base.getNamespace());

					return Mono.just(this);
				}
		).contextWrite(Context.of(LogUtil.METHOD_NAME, "KIRunFxDefinition.applyOverride"));
	}
}
