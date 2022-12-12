package com.fincity.saas.ui.document;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "styleFilteringIndex")
@Accessors(chain = true)
@NoArgsConstructor
public class Style extends AbstractOverridableDTO<Style> {

	private static final long serialVersionUID = 4355909627072800292L;

	private String styleString;

	public Style(Style style) {
		super(style);

		this.styleString = style.styleString;
	}

	@Override
	public Mono<Style> applyOverride(Style base) {

		return Mono.just(this);
	}

	@Override
	public Mono<Style> makeOverride(Style base) {

		return Mono.just(this);

	}
}
