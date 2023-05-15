package com.fincity.saas.core.document;

import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.util.CloneUtil;
import com.fincity.saas.commons.mongo.util.DifferenceApplicator;
import com.fincity.saas.commons.mongo.util.DifferenceExtractor;

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
	private String templateType;

	public Template(Template template) {

		super(template);
		this.templateParts = CloneUtil.cloneMapObject(template.templateParts);
		this.templateType = template.templateType;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Template> applyOverride(Template base) {

		if (base == null)
			return Mono.just(this);

		return DifferenceApplicator.apply(this.templateParts, base.templateParts)
		        .map(e ->
				{
			        this.templateParts = (Map<String, String>) e;
			        if (this.templateType == null)
				        this.templateType = base.templateType;
			        return this;
		        });
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Template> makeOverride(Template base) {

		if (base == null)
			return Mono.just(this);

		return Mono.just(this)
		        .flatMap(e -> DifferenceExtractor.extract(e.templateParts, base.templateParts)
		                .map(k ->
						{
			                e.templateParts = (Map<String, String>) k;

			                if (this.templateType != null && this.templateType.equals(base.templateType))
				                this.templateType = null;

			                return e;
		                }));
	}
}
