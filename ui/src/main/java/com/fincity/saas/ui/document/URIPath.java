package com.fincity.saas.ui.document;

import java.io.Serial;
import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.util.CloneUtil;
import com.fincity.saas.commons.mongo.util.DifferenceApplicator;
import com.fincity.saas.commons.mongo.util.DifferenceExtractor;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.ui.model.PathDefinition;

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
	private String shortCode;

	private Map<String, PathDefinition> pathDefinitions;

	public URIPath(URIPath uriPath) {

		super(uriPath);

		this.pathString = uriPath.pathString;
		this.shortCode = uriPath.shortCode;
		this.pathDefinitions = CloneUtil.cloneMapObject(uriPath.pathDefinitions);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<URIPath> applyOverride(URIPath base) {
		if (base == null) {
			return Mono.just(this);
		}

		return FlatMapUtil.flatMapMonoWithNull(
				() -> DifferenceApplicator.apply(this.pathDefinitions, base.pathDefinitions),
				pDef -> {

					this.pathDefinitions = (Map<String, PathDefinition>) pDef;

					if (this.pathString == null)
						this.pathString = base.pathString;

					if (this.shortCode == null)
						this.shortCode = base.shortCode;

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
				(obj) -> DifferenceExtractor.extract(obj.pathDefinitions, base.pathDefinitions),
				(obj, pDef) -> {
					obj.setPathDefinitions((Map<String, PathDefinition>) pDef);

					if (obj.pathString != null && obj.pathString.equals(base.pathString))
						obj.pathString = null;

					if (obj.shortCode != null && obj.shortCode.equals(base.shortCode))
						obj.shortCode = null;

					return Mono.just(obj);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "URIPath.makeOverride"));
	}

	@Override
	@JsonIgnore
	public String getTransportName() {
		return this.getId();
	}
}
