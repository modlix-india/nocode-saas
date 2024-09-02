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
import com.fincity.saas.ui.enums.URLType;
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
@Document(collection = "uri")
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "uriFilteringIndex")
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public class URI extends AbstractOverridableDTO<URI> {

	@Serial
	private static final long serialVersionUID = 2627066085463822531L;

	private String uriString;

	private String scheme; // null ==> relative URI
	private String fragment;

	// Hierarchical URI components: [//<authority>]<path>[?<query>]
	private String authority; // Registry or server

	// Server-based authority: [<userInfo>@]<host>[:<port>]
	private String userInfo;
	private String host; // null ==> registry-based
	private int port = -1; // -1 ==> undefined

	// Remaining components of hierarchical URIs
	private String path; // null ==> opaque
	private String query;
	private Map<String, String> queryParams;

	private URLType urlType;

	private List<String> whitelist;
	private List<String> blacklist;

	private KIRunFxDefinition kiRunFxDefinition;

	private Map<String, RedirectionDefinition> redirectionDefinitions; // NOSONAR

	public URI(URI uri) {

		super(uri);

		this.uriString = uri.uriString;
		this.scheme = uri.scheme;
		this.fragment = uri.fragment;
		this.authority = uri.authority;
		this.userInfo = uri.userInfo;
		this.host = uri.host;
		this.port = uri.port;
		this.path = uri.path;
		this.query = uri.query;
		this.queryParams = CloneUtil.cloneMapObject(uri.queryParams);
		this.urlType = uri.urlType;
		this.whitelist = CloneUtil.cloneMapList(uri.whitelist);
		this.blacklist = CloneUtil.cloneMapList(uri.blacklist);
		this.kiRunFxDefinition = CloneUtil.cloneObject(uri.kiRunFxDefinition);
		this.redirectionDefinitions = CloneUtil.cloneMapObject(uri.redirectionDefinitions);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<URI> applyOverride(URI base) {
		if (base != null) {
			return FlatMapUtil
					.flatMapMonoWithNull(
							() -> DifferenceApplicator.apply(this.queryParams, base.queryParams),

							qp -> DifferenceApplicator.apply(this.whitelist, base.whitelist),

							(qp, wl) -> DifferenceApplicator.apply(this.blacklist, base.blacklist),

							(qp, wl, bl) -> DifferenceApplicator.apply(this.redirectionDefinitions,
									base.redirectionDefinitions),

							(qp, wl, bl, rd) -> {
								this.queryParams = (Map<String, String>) qp;
								this.whitelist = (List<String>) wl;
								this.blacklist = (List<String>) bl;
								this.redirectionDefinitions = (Map<String, RedirectionDefinition>) rd;

								if (this.uriString == null)
									this.uriString = base.uriString;
								if (this.scheme == null)
									this.scheme = base.scheme;
								if (this.fragment == null)
									this.fragment = base.fragment;
								if (this.authority == null)
									this.authority = base.authority;
								if (this.userInfo == null)
									this.userInfo = base.userInfo;
								if (this.host == null)
									this.host = base.host;
								if (this.port == -1)
									this.port = base.port;
								if (this.path == null)
									this.path = base.path;
								if (this.query == null)
									this.query = base.query;
								if (this.urlType == null)
									this.urlType = base.urlType;
								if (this.kiRunFxDefinition == null)
									this.kiRunFxDefinition = base.kiRunFxDefinition;

								return Mono.just(this);
							})
					.contextWrite(Context.of(LogUtil.METHOD_NAME, "URI.applyOverride"));
		}
		return Mono.just(this);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<URI> makeOverride(URI base) {

		if (base == null)
			return Mono.just(this);

		return FlatMapUtil.flatMapMonoWithNull(

				() -> Mono.just(this),

				obj -> DifferenceExtractor.extract(obj.queryParams, base.queryParams),

				(obj, qp) -> DifferenceExtractor.extract(obj.whitelist, base.whitelist),

				(obj, qp, wl) -> DifferenceExtractor.extract(obj.blacklist, base.blacklist),

				(obj, qp, wl, bl) -> DifferenceExtractor.extract(obj.redirectionDefinitions,
						base.redirectionDefinitions),

				(obj, qp, wl, bl, rd) -> {
					obj.setQueryParams((Map<String, String>) qp);
					obj.setWhitelist((List<String>) wl);
					obj.setBlacklist((List<String>) bl);
					obj.setRedirectionDefinitions((Map<String, RedirectionDefinition>) rd);

					if (obj.uriString != null && obj.uriString.equals(base.uriString))
						obj.uriString = null;
					if (obj.scheme != null && obj.scheme.equals(base.scheme))
						obj.scheme = null;
					if (obj.fragment != null && obj.fragment.equals(base.fragment))
						obj.fragment = null;
					if (obj.authority != null && obj.authority.equals(base.authority))
						obj.authority = null;
					if (obj.userInfo != null && obj.userInfo.equals(base.userInfo))
						obj.userInfo = null;
					if (obj.host != null && obj.host.equals(base.host))
						obj.host = null;
					if (obj.port == base.port)
						obj.port = -1;
					if (obj.path != null && obj.path.equals(base.path))
						obj.path = null;
					if (obj.query != null && obj.query.equals(base.query))
						obj.query = null;
					if (obj.urlType != null && obj.urlType.equals(base.urlType))
						obj.urlType = null;
					if (obj.kiRunFxDefinition != null && obj.kiRunFxDefinition.equals(base.kiRunFxDefinition))
						obj.kiRunFxDefinition = null;

					return Mono.just(obj);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "URI.makeOverride"));
	}

}
