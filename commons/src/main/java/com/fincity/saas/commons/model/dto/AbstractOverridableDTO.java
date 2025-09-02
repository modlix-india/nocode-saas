package com.fincity.saas.commons.model.dto;

import java.io.Serial;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
	public abstract class AbstractOverridableDTO<D extends AbstractOverridableDTO<D>>
		extends AbstractUpdatableDTO<String, String> {

	@Serial
	private static final long serialVersionUID = -7561098495897714431L;

	private String name;
	private String message;
	private String clientCode;
	private String permission;
	private String appCode;
	private String baseClientCode;
	private Boolean notOverridable;
	private String description;
	private String title;

	private int version = 1;

	protected AbstractOverridableDTO(D obj) {
		this.clone(obj);
	}

	public Mono<D> applyActualOverride(D base) {

		if (base != null) {

			if (this.description == null)
				this.description = base.getDescription();

			if (this.title == null)
				this.title = base.getTitle();
		}

		return this.applyOverride(base);
	}

	public Mono<D> makeActualOverride(D base) {
		if (base != null) {
			if (this.description != null && this.description.equals(base.getDescription()))
				this.description = null;

			if (this.title != null && this.title.equals(base.getTitle()))
				this.title = null;
		}

		return this.makeOverride(base);
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
				.setVersion(obj.getVersion())
				.setDescription(obj.getDescription())
				.setTitle(obj.getTitle())
				.setUpdatedAt(obj.getUpdatedAt())
				.setUpdatedBy(obj.getUpdatedBy())
				.setId(obj.getId())
				.setCreatedAt(obj.getCreatedAt())
				.setCreatedBy(obj.getCreatedBy());

		this.notOverridable = obj.getNotOverridable();
	}

	@JsonIgnore
	public String getTransportName() {
		return this.name;
	}
}
