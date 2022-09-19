package com.fincity.saas.ui.document;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public abstract class AbstractUIDTO<D extends AbstractUIDTO<D>> extends AbstractUpdatableDTO<String, String> {

	private static final long serialVersionUID = -7561098495897714431L;

	private String name;
	private String message;
	private String clientCode;
	private String permission;
	private String applicationName;
	private String baseClientCode;

	private int version = 1;

	public abstract Mono<D> applyOverride(D base);

	public abstract Mono<D> makeOverride(D base);
}
