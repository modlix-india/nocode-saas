package com.fincity.saas.ui.document;

import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.util.CloneUtil;
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
@Document(collection = "theme")
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "stylethemeFilteringIndex")
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public class StyleTheme extends AbstractOverridableDTO<StyleTheme> {

	private static final long serialVersionUID = 4355909627072800292L;

	private Map<String, Map<String, String>> variables;

	public StyleTheme(StyleTheme styleTheme) {

		super(styleTheme);
		this.variables = CloneUtil.cloneMapStringMap(styleTheme.variables);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<StyleTheme> applyOverride(StyleTheme base) {

		if (base != null) {

			return FlatMapUtil.flatMapMonoWithNull(

					() -> DifferenceApplicator.apply(this.variables, base.variables),

					v -> {
						this.variables = (Map<String, Map<String, String>>) v;
						return Mono.just(this);
					}).contextWrite(Context.of(LogUtil.METHOD_NAME, "StyleTheme.applyOverride"));
		}
		return Mono.just(this);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<StyleTheme> makeOverride(StyleTheme base) {

		if (base == null)
			return Mono.just(this);

		return Mono.just(this)
				.flatMap(a -> DifferenceExtractor.extract(a.variables, base.variables)
						.map(e -> {
							a.setVariables((Map<String, Map<String, String>>) e);
							return a;
						}));

	}
}
