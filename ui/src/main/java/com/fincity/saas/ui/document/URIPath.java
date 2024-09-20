package com.fincity.saas.ui.document;

import java.io.Serial;
import java.util.List;
import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.http.HttpMethod;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.util.CloneUtil;
import com.fincity.saas.commons.mongo.util.DifferenceApplicator;
import com.fincity.saas.commons.mongo.util.DifferenceExtractor;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.ui.enums.URIType;
import com.fincity.saas.ui.model.KIRunFxDefinition;
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

	private String pathString;
	private URIType uriType;

	private List<String> headers;

	private List<String> whitelist;
	private List<String> blacklist;

	private List<String> referrer;

	private Map<HttpMethod, KIRunFxDefinition> kiRunFxDefinitions;
	private RedirectionDefinition redirectionDefinition;

	public URIPath(URIPath uriPath) {

		super(uriPath);

		this.pathString = uriPath.pathString;
		this.uriType = uriPath.uriType;
		this.headers = CloneUtil.cloneMapList(uriPath.headers);
		this.referrer = CloneUtil.cloneMapList(uriPath.referrer);
		this.whitelist = CloneUtil.cloneMapList(uriPath.whitelist);
		this.blacklist = CloneUtil.cloneMapList(uriPath.blacklist);
		this.kiRunFxDefinitions = CloneUtil.cloneMapObject(uriPath.kiRunFxDefinitions);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<URIPath> applyOverride(URIPath base) {
		if (base == null) {
			return Mono.just(this);
		}

		return FlatMapUtil.flatMapMonoWithNull(
				() -> DifferenceApplicator.apply(this.headers, base.headers),
				(h) -> DifferenceApplicator.apply(this.whitelist, base.whitelist),
				(h, w) -> DifferenceApplicator.apply(this.blacklist, base.blacklist),
				(h, w, b) -> DifferenceApplicator.apply(this.kiRunFxDefinitions, base.kiRunFxDefinitions),
				(h, w, b, k) -> DifferenceApplicator.apply(this.redirectionDefinition,
						base.redirectionDefinition),
				(h, w, b, k, r) -> DifferenceApplicator.apply(this.referrer, base.referrer),
				(h, w, b, k, r, ref) -> {
					this.headers = (List<String>) h;
					this.whitelist = (List<String>) w;
					this.blacklist = (List<String>) b;
					this.referrer = (List<String>) ref;
					this.kiRunFxDefinitions = (Map<HttpMethod, KIRunFxDefinition>) k;
					this.redirectionDefinition = (RedirectionDefinition) r;

					if (this.pathString == null)
						this.pathString = base.pathString;
					if (this.uriType == null)
						this.uriType = base.uriType;

					return Mono.just(this);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "URIPath.applyOverride"));
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<URIPath> makeOverride(URIPath base) {
		if (base == null) {
			return Mono.just(this);
		}

		return FlatMapUtil.flatMapMonoWithNull(
				() -> Mono.just(this),
				(obj) -> DifferenceExtractor.extract(obj.headers, base.headers),
				(obj, h) -> DifferenceExtractor.extract(obj.whitelist, base.whitelist),
				(obj, h, w) -> DifferenceExtractor.extract(obj.blacklist, base.blacklist),
				(obj, h, w, b) -> DifferenceExtractor.extract(obj.kiRunFxDefinitions, base.kiRunFxDefinitions),
				(obj, h, w, b, k) -> DifferenceExtractor.extract(obj.redirectionDefinition,
						base.redirectionDefinition),
				(obj, h, w, b, k, r) -> DifferenceExtractor.extract(obj.referrer, base.referrer),
				(obj, h, w, b, k, r, ref) -> {
					obj.setHeaders((List<String>) h);
					obj.setWhitelist((List<String>) w);
					obj.setBlacklist((List<String>) b);
					obj.setKiRunFxDefinitions((Map<HttpMethod, KIRunFxDefinition>) k);
					obj.setRedirectionDefinition((RedirectionDefinition) r);
					obj.setReferrer((List<String>) ref);

					if (obj.pathString != null && obj.pathString.equals(base.pathString))
						obj.pathString = null;
					if (obj.uriType != null && obj.uriType.equals(base.uriType))
						obj.uriType = null;

					return Mono.just(obj);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "URIPath.makeOverride"));
	}

}
