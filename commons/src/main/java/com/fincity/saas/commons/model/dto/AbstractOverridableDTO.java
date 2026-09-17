package com.fincity.saas.commons.model.dto;

import java.io.Serial;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.difference.IDifferentiable;
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
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public abstract class AbstractOverridableDTO<D extends AbstractOverridableDTO<D>>
        extends AbstractUpdatableDTO<String, String> implements IDifferentiable<D> {

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
     * Whether this object is visible on the live surface.
     *
     * null means legacy, treated as published: every document already in Mongo
     * predates this field, so no migration or backfill is needed and
     * Boolean.FALSE.equals(...) is the null-safe check to use everywhere.
     *
     * Only a create made in draft mode writes FALSE. Creation itself is never
     * drafted, so the object is real and fully addressable from the moment it
     * exists, it simply does not render until first published.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Boolean published;

    /**
     * The plan for this object: what it is meant to be, as opposed to what it is.
     *
     * Overridden key by key like properties, never as a whole value, so a tenant
     * that restates one entry keeps receiving base corrections for every other.
     *
     * NO ARRAYS AT ANY DEPTH. DifferenceExtractor treats a List as an opaque
     * value (DifferenceExtractor.extract(Object, Object)), so a tenant that
     * touches one element copies the whole list into its override and is
     * silently detached from the base from then on, with nothing failing.
     * A list is written as a uid-keyed map carrying an integer order field.
     *
     * null means legacy or hand-built, exactly as published does: no migration,
     * no backfill, and an object with no plan is a normal object.
     */
    private Map<String, Object> blueprint; // NOSONAR

    private int version = 1;

    protected AbstractOverridableDTO(D obj) {
        this.clone(obj);
    }

    @SuppressWarnings("unchecked")
    public Mono<D> applyActualOverride(D base) {

        if (base == null)
            return this.applyOverride(null);

        if (this.description == null)
            this.description = base.getDescription();

        if (this.title == null)
            this.title = base.getTitle();

        return FlatMapUtil
                .flatMapMonoWithNull(() -> DifferenceApplicator.apply(this.blueprint, base.getBlueprint()),

                        bp -> {
                            this.blueprint = (Map<String, Object>) bp;
                            return this.applyOverride(base);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractOverridableDTO.applyActualOverride"));
    }

    @SuppressWarnings("unchecked")
    public Mono<D> makeActualOverride(D base) {

        if (base == null)
            return this.extractDifference(null);

        if (this.description != null && this.description.equals(base.getDescription()))
            this.description = null;

        if (this.title != null && this.title.equals(base.getTitle()))
            this.title = null;

        return FlatMapUtil
                .flatMapMonoWithNull(() -> DifferenceExtractor.extract(this.blueprint, base.getBlueprint()),

                        bp -> {
                            this.blueprint = (Map<String, Object>) bp;
                            return this.extractDifference(base);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractOverridableDTO.makeActualOverride"));
    }

    public abstract Mono<D> applyOverride(D base);

    public abstract Mono<D> extractDifference(D base);

    protected void clone(D obj) {

        this.setName(obj.getName())
                .setMessage(obj.getMessage())
                .setClientCode(obj.getClientCode())
                .setPermission(obj.getPermission())
                .setAppCode(obj.getAppCode())
                .setBaseClientCode(obj.getBaseClientCode())
                .setVersion(obj.getVersion())
                .setPublished(obj.getPublished())
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
