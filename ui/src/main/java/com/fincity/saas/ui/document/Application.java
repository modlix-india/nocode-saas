package com.fincity.saas.ui.document;

import java.util.Map;

import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.saas.ui.util.DifferenceApplicator;
import com.fincity.saas.ui.util.DifferenceExtractor;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@Accessors(chain = true)
public class Application extends AbstractUIDTO<Application> {

	private static final long serialVersionUID = 4162610982706108795L;

	private Map<String, Map<String, String>> translations;
	private Map<String, Map<String, String>> languages;
	private Map<String, Object> properties; // NOSONAR

	private String defaultLanguage;

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Application> applyOverride(Application base) {

		if (base != null) {
			this.translations = (Map<String, Map<String, String>>) DifferenceApplicator.jsonMap(this.translations,
			        base.translations);
			this.languages = (Map<String, Map<String, String>>) DifferenceApplicator.jsonMap(this.languages,
			        base.languages);
			this.properties = (Map<String, Object>) DifferenceApplicator.jsonMap(this.properties, base.properties);
			if (this.defaultLanguage == null)
				this.defaultLanguage = base.defaultLanguage;
		}
		return Mono.just(this);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Application> makeOverride(Application base) {

		if (base == null)
			return Mono.just(this);

		return Mono.just(this)
		        .flatMap(a -> DifferenceExtractor.jsonMap(a.translations, base.translations)
		                .map(e ->
						{
			                a.setTranslations((Map<String, Map<String, String>>) e);
			                return a;
		                }))
		        .flatMap(a -> DifferenceExtractor.jsonMap(a.languages, base.languages)
		                .map(e ->
						{
			                a.setLanguages((Map<String, Map<String, String>>) e);
			                return a;
		                }))
		        .flatMap(a -> DifferenceExtractor.jsonMap(a.properties, base.properties)
		                .map(e ->
						{
			                a.setProperties((Map<String, Object>) e);
			                return a;
		                }))
		        .map(a -> {
		        	
		        	if (this.defaultLanguage != null && this.defaultLanguage.equals(base.defaultLanguage))
		        		this.defaultLanguage = null;
		        	
		        	return a;
		        });
	}

}
