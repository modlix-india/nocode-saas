package com.fincity.saas.ui.document;

import java.util.Map;

import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.nocode.reactor.util.FlatMapUtil;
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

			return FlatMapUtil.flatMapMonoWithNull(
			        () -> DifferenceApplicator.apply(this.translations, base.translations),

			        t -> DifferenceApplicator.apply(this.languages, base.languages),

			        (t, l) -> DifferenceApplicator.apply(this.properties, base.properties),

			        (t, l, p) ->
					{

				        this.translations = (Map<String, Map<String, String>>) t;
				        this.languages = (Map<String, Map<String, String>>) l;
				        this.properties = (Map<String, Object>) p;

				        if (this.defaultLanguage == null)
					        this.defaultLanguage = base.defaultLanguage;

				        return Mono.just(this);
			        });
		}
		return Mono.just(this);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Application> makeOverride(Application base) {

		Mono<Application> starting = Mono.just(this);
		if (base == null)
			return starting;

		return FlatMapUtil.flatMapMonoWithNullLog(

		        () -> starting,

		        obj -> DifferenceExtractor.extract(obj.translations, base.translations),

		        (obj, tr) -> DifferenceExtractor.extract(obj.languages, base.languages),

		        (obj, tr, lang) -> DifferenceExtractor.extract(obj.properties, base.properties),

		        (obj, tr, lang, props) ->
				{

			        obj.setTranslations((Map<String, Map<String, String>>) tr);

			        obj.setLanguages((Map<String, Map<String, String>>) lang);

			        obj.setProperties((Map<String, Object>) props);

			        if (obj.defaultLanguage != null && obj.defaultLanguage.equals(base.defaultLanguage))
				        obj.defaultLanguage = null;

			        return Mono.just(obj);
		        });
	}

}
