package com.fincity.saas.ui.document;

import java.io.Serial;
import java.util.List;

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
	private Boolean isAuthenticated;

	private List<HttpMethod> httpMethods;
	private List<String> headers;
	private List<String> queryParams;
	private List<String> whitelist;
	private List<String> blacklist;

	private KIRunFxDefinition kiRunFxDefinition;
	private RedirectionDefinition redirectionDefinition;

	public URIPath(URIPath uriPath) {

		super(uriPath);

		this.pathString = uriPath.pathString;
		this.uriType = uriPath.uriType;
		this.isAuthenticated = uriPath.isAuthenticated;
		this.httpMethods = CloneUtil.cloneMapList(uriPath.httpMethods);
		this.headers = CloneUtil.cloneMapList(uriPath.headers);
		this.queryParams = CloneUtil.cloneMapList(uriPath.queryParams);
		this.whitelist = CloneUtil.cloneMapList(uriPath.whitelist);
		this.blacklist = CloneUtil.cloneMapList(uriPath.blacklist);
		this.kiRunFxDefinition = CloneUtil.cloneObject(uriPath.kiRunFxDefinition);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<URIPath> applyOverride(URIPath base) {
		if (base == null) {
			return Mono.just(this);
		}

		return FlatMapUtil.flatMapMonoWithNull(
				() -> DifferenceApplicator.apply(this.httpMethods, base.httpMethods),
				hm -> DifferenceApplicator.apply(this.headers, base.headers),
				(hm, h) -> DifferenceApplicator.apply(this.queryParams, base.queryParams),
				(hm, h, qp) -> DifferenceApplicator.apply(this.whitelist, base.whitelist),
				(hm, h, qp, w) -> DifferenceApplicator.apply(this.blacklist, base.blacklist),
				(hm, h, qp, w, b) -> DifferenceApplicator.apply(this.kiRunFxDefinition, base.kiRunFxDefinition),
				(hm, h, qp, w, b, k) -> DifferenceApplicator.apply(this.redirectionDefinition,
						base.redirectionDefinition),
				(hm, h, qp, w, b, k, r) -> {
					this.httpMethods = (List<HttpMethod>) hm;
					this.headers = (List<String>) h;
					this.queryParams = (List<String>) qp;
					this.whitelist = (List<String>) w;
					this.blacklist = (List<String>) b;
					this.kiRunFxDefinition = (KIRunFxDefinition) k;
					this.redirectionDefinition = (RedirectionDefinition) r;

					if (this.pathString == null)
						this.pathString = base.pathString;
					if (this.uriType == null)
						this.uriType = base.uriType;
					if (this.isAuthenticated == null)
						this.isAuthenticated = base.isAuthenticated;

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
				obj -> DifferenceExtractor.extract(obj.httpMethods, base.httpMethods),
				(obj, hm) -> DifferenceExtractor.extract(obj.headers, base.headers),
				(obj, hm, h) -> DifferenceExtractor.extract(obj.queryParams, base.queryParams),
				(obj, hm, h, qp) -> DifferenceExtractor.extract(obj.whitelist, base.whitelist),
				(obj, hm, h, qp, w) -> DifferenceExtractor.extract(obj.blacklist, base.blacklist),
				(obj, hm, h, qp, w, b) -> DifferenceExtractor.extract(obj.kiRunFxDefinition, base.kiRunFxDefinition),
				(obj, hm, h, qp, w, b, k) -> DifferenceExtractor.extract(obj.redirectionDefinition,
						base.redirectionDefinition),
				(obj, hm, h, qp, w, b, k, r) -> {
					obj.setHttpMethods((List<HttpMethod>) hm);
					obj.setHeaders((List<String>) h);
					obj.setQueryParams((List<String>) qp);
					obj.setWhitelist((List<String>) w);
					obj.setBlacklist((List<String>) b);
					obj.setKiRunFxDefinition((KIRunFxDefinition) k);
					obj.setRedirectionDefinition((RedirectionDefinition) r);

					if (obj.pathString != null && obj.pathString.equals(base.pathString))
						obj.pathString = null;
					if (obj.uriType != null && obj.uriType.equals(base.uriType))
						obj.uriType = null;
					if (obj.isAuthenticated != null && obj.isAuthenticated.equals(base.isAuthenticated))
						obj.isAuthenticated = null;

					return Mono.just(obj);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "URIPath.makeOverride"));
	}

}
