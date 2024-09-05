package com.fincity.saas.ui.document;

import java.io.Serial;
import java.util.List;
import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.util.CloneUtil;
import com.fincity.saas.commons.mongo.util.DifferenceApplicator;
import com.fincity.saas.commons.mongo.util.DifferenceExtractor;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.ui.enums.URIType;
import com.fincity.saas.ui.model.KIRunFxDefinition;
import com.fincity.saas.ui.model.PathDefinition;
import com.fincity.saas.ui.model.RedirectionDefinition;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "uri_path")
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "uriFilteringIndex")
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public class URIPath extends AbstractOverridableDTO<URIPath> {

	@Serial
	private static final long serialVersionUID = 2627066085463822531L;

	private URIType uriType;

	private PathDefinition pathDefinition;

	private List<String> headers;
	private List<String> whitelist;
	private List<String> blacklist;

	private KIRunFxDefinition kiRunFxDefinition;

	private Map<String, RedirectionDefinition> redirectionDefinitions; // NOSONAR

	public URIPath(URIPath uriPath) {

		super(uriPath);

		this.uriType = uriPath.uriType;
		this.pathDefinition = CloneUtil.cloneObject(uriPath.pathDefinition);
		this.headers = CloneUtil.cloneMapList(uriPath.headers);
		this.whitelist = CloneUtil.cloneMapList(uriPath.whitelist);
		this.blacklist = CloneUtil.cloneMapList(uriPath.blacklist);
		this.kiRunFxDefinition = CloneUtil.cloneObject(uriPath.kiRunFxDefinition);
		this.redirectionDefinitions = CloneUtil.cloneMapObject(uriPath.redirectionDefinitions);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<URIPath> applyOverride(URIPath base) {
		if (base != null) {
			return FlatMapUtil.flatMapMonoWithNull(
					() -> DifferenceApplicator.apply(this.pathDefinition, base.pathDefinition),
					ph -> DifferenceApplicator.apply(this.headers, base.headers),
					(ph, he) -> DifferenceApplicator.apply(this.whitelist, base.whitelist),
					(ph, he, wh) -> DifferenceApplicator.apply(this.blacklist, base.blacklist),
					(ph, he, wh, bl) -> DifferenceApplicator.apply(this.kiRunFxDefinition, base.kiRunFxDefinition),
					(ph, he, wh, bl, kr) -> DifferenceApplicator.apply(this.redirectionDefinitions,
							base.redirectionDefinitions),
					(ph, he, wh, bl, kr, re) -> {

						this.pathDefinition = (PathDefinition) ph;
						this.headers = (List<String>) he;
						this.whitelist = (List<String>) wh;
						this.blacklist = (List<String>) bl;
						this.kiRunFxDefinition = (KIRunFxDefinition) kr;
						this.redirectionDefinitions = (Map<String, RedirectionDefinition>) re;

						if (this.uriType == null)
							this.uriType = base.uriType;

						return Mono.just(this);
					}).contextWrite(Context.of(LogUtil.METHOD_NAME, "URIPath.applyOverride"));
		}
		return Mono.just(this);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<URIPath> makeOverride(URIPath base) {
		Mono<URIPath> starting = Mono.just(this);
		if (base == null)
			return starting;

		return FlatMapUtil.flatMapMonoWithNull(
				() -> starting,
				obj -> DifferenceExtractor.extract(obj.whitelist, base.whitelist),
				(obj, w) -> DifferenceExtractor.extract(obj.blacklist, base.blacklist),
				(obj, w, b) -> DifferenceExtractor.extract(obj.redirectionDefinitions, base.redirectionDefinitions),
				(obj, w, b, r) -> {
					obj.setWhitelist((List<String>) w);
					obj.setBlacklist((List<String>) b);
					obj.setRedirectionDefinitions((Map<String, RedirectionDefinition>) r);

					if (obj.uriType != null && obj.uriType.equals(base.uriType))
						obj.uriType = null;
					if (obj.headers != null && obj.headers.equals(base.headers))
						obj.headers = null;
					if (obj.kiRunFxDefinition != null && obj.kiRunFxDefinition.equals(base.kiRunFxDefinition))
						obj.kiRunFxDefinition = null;

					return Mono.just(obj);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "URIPath.makeOverride"));
	}

}
