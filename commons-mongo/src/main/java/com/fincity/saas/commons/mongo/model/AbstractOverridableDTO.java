package com.fincity.saas.commons.mongo.model;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@NoArgsConstructor
public abstract class AbstractOverridableDTO<D extends AbstractOverridableDTO<D>> extends AbstractUpdatableDTO<String, String> {

	private static final long serialVersionUID = -7561098495897714431L;

	private String name;
	private String message;
	private String clientCode;
	private String permission;
	private String appCode;
	private String baseClientCode;

	private int version = 1;

	protected AbstractOverridableDTO(D obj) {
		this.clone(obj);
	}

	public abstract Mono<D> applyOverride(D base);

	public abstract Mono<D> makeOverride(D base);

	protected void clone(D obj) {

		this.setName(obj.getName())
		        .setMessage(obj.getMessage())
		        .setClientCode(obj.getClientCode())
		        .setPermission(obj.getPermission())
		        .setAppCode(obj.getAppCode())
		        .setBaseClientCode(obj.getBaseClientCode())
		        .setVersion(obj.getVersion());

		this.setUpdatedAt(obj.getUpdatedAt())
		        .setUpdatedBy(obj.getUpdatedBy());

		this.setId(obj.getId())
		        .setCreatedAt(obj.getCreatedAt())
		        .setCreatedBy(obj.getCreatedBy());
	}
}
