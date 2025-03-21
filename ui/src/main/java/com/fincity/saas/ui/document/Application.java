package com.fincity.saas.ui.document;

import java.util.Map;

import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.util.CloneUtil;
import com.fincity.saas.commons.util.DifferenceApplicator;
import com.fincity.saas.commons.util.DifferenceExtractor;
import com.fincity.saas.commons.util.LogUtil;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public class Application extends AbstractOverridableDTO<Application> {

	private static final long serialVersionUID = 4162610982706108795L;

	private Map<String, Map<String, String>> translations;
	private Map<String, Map<String, String>> languages;
	private Map<String, Object> properties; // NOSONAR

	private String defaultLanguage;

	public Application(Application app) {

		super(app);

		this.translations = CloneUtil.cloneMapStringMap(app.getTranslations());
		this.languages = CloneUtil.cloneMapStringMap(app.getLanguages());
		this.properties = CloneUtil.cloneMapObject(app.getProperties());

		this.defaultLanguage = app.defaultLanguage;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Application> applyOverride(Application base) {

		if (base != null) {

			return FlatMapUtil.flatMapMonoWithNull(
					() -> DifferenceApplicator.apply(this.translations, base.translations),

					t -> DifferenceApplicator.apply(this.languages, base.languages),

					(t, l) -> DifferenceApplicator.apply(this.properties, base.properties),

					(t, l, p) -> {

						this.translations = (Map<String, Map<String, String>>) t;
						this.languages = (Map<String, Map<String, String>>) l;
						this.properties = (Map<String, Object>) p;

						if (this.defaultLanguage == null)
							this.defaultLanguage = base.defaultLanguage;

						return Mono.just(this);
					}).contextWrite(Context.of(LogUtil.METHOD_NAME, "Application.applyOverride"));
		}
		return Mono.just(this);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Application> makeOverride(Application base) {

		Mono<Application> starting = Mono.just(this);
		if (base == null)
			return starting;

		return FlatMapUtil.flatMapMonoWithNull(

				() -> starting,

				obj -> DifferenceExtractor.extract(obj.translations, base.translations),

				(obj, tr) -> DifferenceExtractor.extract(obj.languages, base.languages),

				(obj, tr, lang) -> DifferenceExtractor.extract(obj.properties, base.properties),

				(obj, tr, lang, props) -> {

					obj.setTranslations((Map<String, Map<String, String>>) tr);

					obj.setLanguages((Map<String, Map<String, String>>) lang);

					obj.setProperties((Map<String, Object>) props);

					if (obj.defaultLanguage != null && obj.defaultLanguage.equals(base.defaultLanguage))
						obj.defaultLanguage = null;

					return Mono.just(obj);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "Application.makeOverride"));
	}

}
