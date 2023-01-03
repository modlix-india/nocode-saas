package com.fincity.saas.core.document;

import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.util.CloneUtil;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "templateFilteringIndex")
@Accessors(chain = true)
@NoArgsConstructor
public class Template extends AbstractOverridableDTO<Template> {

	private static final long serialVersionUID = -6427509976748513994L;

	private Map<String, String> templateParts;

	public Template(Template template) {

		super(template);
		this.templateParts = CloneUtil.cloneMapObject(template.templateParts);
	}

	@Override
	public Mono<Template> applyOverride(Template base) {
		return Mono.just(this);
	}

	@Override
	public Mono<Template> makeOverride(Template base) {
		return Mono.just(this);
	}
}
