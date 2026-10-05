package com.fincity.saas.ui.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
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

	// Copy constructor so CloneUtil can defensively copy a PathDefinition (it looks up a
	// <self>(<self>) constructor). Lists are copied into fresh instances; the enum and the
	// nested defs are replaced (not mutated) by applyOverride, so reference copies are safe.
	public PathDefinition(PathDefinition other) {
		if (other == null)
			return;
		this.uriType = other.uriType;
		this.headers = other.headers == null ? null : new ArrayList<>(other.headers);
		this.whitelist = other.whitelist == null ? null : new ArrayList<>(other.whitelist);
		this.blacklist = other.blacklist == null ? null : new ArrayList<>(other.blacklist);
		this.referrer = other.referrer == null ? null : new ArrayList<>(other.referrer);
		this.kiRunFxDefinition = other.kiRunFxDefinition;
		this.redirectionDefinition = other.redirectionDefinition;
	}

	@SuppressWarnings("unchecked")
	@Override
	/**
	 * {@code this} is the BASE and {@code inc} the DERIVED.
	 *
	 * A nested IDifferentiable is reached as existing.extractDifference(incoming),
	 * which is the OPPOSITE way round from a document's extractDifference(base).
	 * Every nested extract here was written the document way, so base and derived
	 * were swapped and a derived client's lists came back as the base's.
	 */
	public Mono<PathDefinition> extractDifference(PathDefinition inc) {

		if (inc == null) {
			return Mono.just(this);
		}

		return FlatMapUtil.flatMapMonoWithNull(
				() -> DifferenceExtractor.extract(inc.headers, this.headers),
				he -> DifferenceExtractor.extract(inc.whitelist, this.whitelist),
				(he, wh) -> DifferenceExtractor.extract(inc.blacklist, this.blacklist),
				(he, wh, bl) -> DifferenceExtractor.extract(inc.referrer, this.referrer),
				(he, wh, bl, re) -> DifferenceExtractor.extract(inc.kiRunFxDefinition, this.kiRunFxDefinition),
				(he, wh, bl, re, ki) -> DifferenceExtractor.extract(inc.redirectionDefinition,
						this.redirectionDefinition),
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

		return FlatMapUtil.flatMapMonoWithNull(
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

					if (this.getUriType() == null)
						this.setUriType(override.getUriType());

					return Mono.just(this);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "PathDefinition.applyOverride"));
	}

	@JsonIgnore
	public boolean isValidType() {

		if (this.uriType == null) {
			return false;
		}

		// Each type carries exactly its own definition and not the other's. The
		// REDIRECTION arm used to be a copy of the KIRUN_FUNCTION one, which no
		// REDIRECTION could ever satisfy: URIPathService.setPathDefinitions has
		// just populated redirectionDefinition and nulled kiRunFxDefinition for
		// precisely this type, so every redirect create failed URI_INVALID_TYPE.
		return switch (this.getUriType()) {
			case KIRUN_FUNCTION -> (this.kiRunFxDefinition != null && this.redirectionDefinition == null);
			case REDIRECTION -> (this.redirectionDefinition != null && this.kiRunFxDefinition == null);
		};
	}
}
