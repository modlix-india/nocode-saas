package com.fincity.saas.core.document;

import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.util.CloneUtil;
import com.fincity.saas.commons.mongo.util.DifferenceApplicator;
import com.fincity.saas.commons.mongo.util.DifferenceExtractor;
import com.fincity.saas.commons.util.StringUtil;

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

	private Map<String, Map<String, String>> templateParts;
	private Map<String, String> resources;
	private String defaultLanguage;
	private String templateType;
	private String toExpression;
	private String fromExpression;
	private String languageExpression;

	public Template(Template template) {

		super(template);
		this.templateParts = CloneUtil.cloneMapObject(template.templateParts);
		this.resources = CloneUtil.cloneMapObject(template.resources);
		this.templateType = template.templateType;
		this.languageExpression = template.languageExpression;
		this.defaultLanguage = template.defaultLanguage;
		this.toExpression = template.toExpression;
		this.fromExpression = template.fromExpression;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Template> applyOverride(Template base) {

		if (base == null)
			return Mono.just(this);

		return FlatMapUtil.flatMapMono(

		        () -> DifferenceApplicator.apply(this.templateParts, base.templateParts),

		        tempParts -> DifferenceApplicator.apply(this.resources, base.resources),

		        (tempParts, rsrc) ->
				{

			        this.templateParts = (Map<String, Map<String, String>>) tempParts;
			        this.resources = (Map<String, String>) rsrc;

			        if (this.templateType == null)
				        this.templateType = base.templateType;

			        if (this.defaultLanguage == null)
				        this.defaultLanguage = base.defaultLanguage;

			        if (this.toExpression == null)
				        this.toExpression = base.toExpression;

			        if (this.fromExpression == null)
				        this.fromExpression = base.fromExpression;

			        if (this.languageExpression == null)
				        this.languageExpression = base.languageExpression;

			        return Mono.just(this);
		        }

		);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Template> makeOverride(Template base) {

		if (base == null)
			return Mono.just(this);

		return FlatMapUtil.flatMapMono(

		        () -> DifferenceExtractor.extract(this.templateParts, base.templateParts),

		        tempParts -> DifferenceExtractor.extract(this.resources, base.resources),

		        (temParts, rsrc) ->
				{
			        this.templateParts = (Map<String, Map<String, String>>) temParts;
			        this.resources = (Map<String, String>) rsrc;

			        if (StringUtil.safeEquals(this.templateType, base.templateType))
				        this.templateType = null;

			        if (StringUtil.safeEquals(this.defaultLanguage, base.defaultLanguage))
				        this.defaultLanguage = null;

			        if (StringUtil.safeEquals(this.toExpression, base.toExpression))
				        this.toExpression = null;

			        if (StringUtil.safeEquals(this.fromExpression, base.fromExpression))
				        this.fromExpression = null;

			        if (StringUtil.safeEquals(this.languageExpression, base.languageExpression))
				        this.languageExpression = null;

			        return Mono.just(this);
		        }

		);
	}
}
