package com.modlix.saas.commons2.model.dto;

import java.io.Serial;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.modlix.saas.commons2.util.CloneUtil;
import com.modlix.saas.commons2.util.DifferenceApplicator;
import com.modlix.saas.commons2.util.DifferenceExtractor;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

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

	/**
	 * The plan for this object: what it is meant to be, as opposed to what it is.
	 *
	 * Overridden key by key like properties, never as a whole value, so a tenant
	 * that restates one entry keeps receiving base corrections for every other.
	 *
	 * NO ARRAYS AT ANY DEPTH. DifferenceExtractor treats a List as an opaque
	 * value, so a tenant that touches one element copies the whole list into its
	 * override and is silently detached from the base from then on, with nothing
	 * failing. A list is written as a uid-keyed map carrying an integer order.
	 *
	 * null means legacy or hand-built: no migration, no backfill, and an object
	 * with no plan is a normal object.
	 */
	private Map<String, Object> blueprint; // NOSONAR

	private int version = 1;

	protected AbstractOverridableDTO(D obj) {
		this.clone(obj);
	}

	@SuppressWarnings("unchecked")
	public D applyActualOverride(D base) {

		if (base != null) {

			if (this.description == null)
				this.description = base.getDescription();

			if (this.title == null)
				this.title = base.getTitle();

			this.blueprint = (Map<String, Object>) DifferenceApplicator.apply(this.blueprint, base.getBlueprint());
		}

		return this.applyOverride(base);
	}

	@SuppressWarnings("unchecked")
	public D makeActualOverride(D base) {
		if (base != null) {
			if (this.description != null && this.description.equals(base.getDescription()))
				this.description = null;

			if (this.title != null && this.title.equals(base.getTitle()))
				this.title = null;

			this.blueprint = (Map<String, Object>) DifferenceExtractor.extract(this.blueprint, base.getBlueprint());
		}

		return this.makeOverride(base);
	}

	public abstract D applyOverride(D base);

	public abstract D makeOverride(D base);

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
				.setBlueprint(CloneUtil.cloneMapObject(obj.getBlueprint()))
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
