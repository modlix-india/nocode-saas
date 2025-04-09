package com.fincity.saas.ui.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.difference.IDifferentiable;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.DifferenceApplicator;
import com.fincity.saas.commons.util.DifferenceExtractor;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.ui.enums.URIType;

import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Data
@NoArgsConstructor
public class PathDefinition implements Serializable, IDifferentiable<PathDefinition> {

	@Serial
	private static final long serialVersionUID = 2608832771490212458L;

	private URIType uriType;

	private List<String> headers;

	private List<String> whitelist;
	private List<String> blacklist;

	private List<String> referrer;

	private KIRunFxDefinition kiRunFxDefinition;
	private RedirectionDefinition redirectionDefinition;

	@SuppressWarnings("unchecked")
	@Override
	public Mono<PathDefinition> extractDifference(PathDefinition inc) {

		if (inc == null) {
			return Mono.just(this);
		}

		return FlatMapUtil.flatMapMono(
				() -> DifferenceExtractor.extract(this.headers, inc.headers),
				he -> DifferenceExtractor.extract(this.whitelist, inc.whitelist),
				(he, wh) -> DifferenceExtractor.extract(this.blacklist, inc.blacklist),
				(he, wh, bl) -> DifferenceExtractor.extract(this.referrer, inc.referrer),
				(he, wh, bl, re) -> DifferenceExtractor.extract(this.kiRunFxDefinition, inc.kiRunFxDefinition),
				(he, wh, bl, re, ki) -> DifferenceExtractor.extract(this.redirectionDefinition,
						inc.redirectionDefinition),
				(he, wh, bl, re, ki, rd) -> {
					PathDefinition diff = new PathDefinition();

					diff.setHeaders((List<String>) he);
					diff.setWhitelist((List<String>) wh);
					diff.setBlacklist((List<String>) bl);
					diff.setReferrer((List<String>) re);
					diff.setKiRunFxDefinition((KIRunFxDefinition) ki);
					diff.setRedirectionDefinition((RedirectionDefinition) rd);

					if (!CommonsUtil.safeEquals(this.uriType, inc.uriType))
						diff.setUriType(inc.uriType);

					return Mono.just(diff);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "PathDefinition.extractDifference"));
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<PathDefinition> applyOverride(PathDefinition override) {

		if (override == null) {
			return Mono.just(this);
		}

		return FlatMapUtil.flatMapMono(
				() -> DifferenceApplicator.apply(this.headers, override.headers),
				he -> DifferenceApplicator.apply(this.whitelist, override.whitelist),
				(he, wh) -> DifferenceApplicator.apply(this.blacklist, override.blacklist),
				(he, wh, bl) -> DifferenceApplicator.apply(this.referrer, override.referrer),
				(he, wh, bl, re) -> DifferenceApplicator.apply(this.kiRunFxDefinition, override.kiRunFxDefinition),
				(he, wh, bl, re, ki) -> DifferenceApplicator.apply(this.redirectionDefinition,
						override.redirectionDefinition),
				(he, wh, bl, re, ki, rd) -> {
					this.setHeaders((List<String>) he);
					this.setWhitelist((List<String>) wh);
					this.setBlacklist((List<String>) bl);
					this.setReferrer((List<String>) re);
					this.setKiRunFxDefinition((KIRunFxDefinition) ki);
					this.setRedirectionDefinition((RedirectionDefinition) rd);

					if (override.getUriType() != null)
						this.setUriType(override.getUriType());

					return Mono.just(this);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "PathDefinition.applyOverride"));
	}

	@JsonIgnore
	public boolean isValidType() {

		if (this.uriType == null) {
			return false;
		}

		return switch (this.getUriType()) {
			case KIRUN_FUNCTION -> (this.kiRunFxDefinition != null && this.redirectionDefinition == null);
			case REDIRECTION -> (this.redirectionDefinition == null && this.kiRunFxDefinition != null);
		};
	}
}
