package com.fincity.saas.entity.processor.dto.rule;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.EnumSchemaUtil;
import com.fincity.saas.entity.processor.enums.rule.DistributionType;
import com.fincity.saas.entity.processor.model.common.UserDistribution;
import com.fincity.saas.entity.processor.model.request.rule.RuleInfoRequest;
import com.fincity.saas.entity.processor.model.request.rule.RuleRequest;
import com.fincity.saas.entity.processor.relations.resolvers.field.UserFieldResolver;
import com.google.gson.JsonPrimitive;
import java.io.Serial;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;
import org.springframework.data.annotation.Version;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public abstract class Rule<T extends Rule<T>> extends BaseUpdatableDto<T> {

    @Serial
    private static final long serialVersionUID = 3634716140733876196L;

    @Version
    private int version = 1;

    private ULong stageId;
    private Integer order;
    private boolean isDefault;
    private boolean breakAtFirstMatch = true;
    private boolean isSimple = true;
    private boolean isComplex;

    private DistributionType userDistributionType;
    private UserDistribution userDistribution;
    private ULong lastAssignedUserId;

    protected Rule() {
        super();
        this.relationsMap.put(Fields.stageId, EntitySeries.STAGE.getTable());
        this.relationsResolverMap.put(UserFieldResolver.class, Fields.lastAssignedUserId);
    }

    protected Rule(Rule<T> rule) {
        super(rule);
        this.version = rule.version;
        this.stageId = rule.stageId;
        this.order = rule.order;
        this.isDefault = rule.isDefault;
        this.breakAtFirstMatch = rule.breakAtFirstMatch;
        this.isSimple = rule.isSimple;
        this.isComplex = rule.isComplex;
        this.userDistributionType = rule.userDistributionType;
        this.userDistribution = CloneUtil.cloneObject(rule.userDistribution);
        this.lastAssignedUserId = rule.lastAssignedUserId;
    }

    public abstract ULong getEntityId();

    public abstract T setEntityId(ULong entityId);

    public T of(RuleRequest ruleRequest) {

        RuleInfoRequest ruleInfoRequest = ruleRequest.getRule();

        if (ruleInfoRequest == null) return (T) this;

        this.setName(ruleInfoRequest.getName())
                .setDescription(ruleInfoRequest.getDescription())
                .setStageId(
                        ruleInfoRequest.getStageId() != null
                                ? ruleInfoRequest.getStageId().getULongId()
                                : null)
                .setIsDefault(ruleInfoRequest.isDefault())
                .setBreakAtFirstMatch(ruleInfoRequest.isBreakAtFirstMatch())
                .setSimple(ruleRequest.isSimple())
                .setComplex(ruleRequest.isComplex())
                .setUserDistributionType(ruleInfoRequest.getUserDistributionType())
                .setUserDistribution(ruleInfoRequest.getUserDistribution());

        return (T) this;
    }

    public T setIsDefault(boolean isDefault) {
        this.isDefault = isDefault;
        if (isDefault) this.stageId = null;
        return (T) this;
    }

    public T setComplex(boolean isComplex) {
        this.isComplex = isComplex;
        if (isComplex) this.isSimple = false;
        return (T) this;
    }

    public T setSimple(boolean isSimple) {
        this.isSimple = isSimple;
        if (isSimple) this.isComplex = false;
        return (T) this;
    }

    @Override
    public Schema getSchema() {

        Schema schema = super.getSchema();

        Map<String, Schema> props = schema.getProperties();
        props.put(Fields.version, Schema.ofInteger(Fields.version).setMinimum(1));
        props.put(Fields.stageId, Schema.ofLong(Fields.stageId).setMinimum(1));
        props.put(Fields.order, Schema.ofInteger(Fields.order).setMinimum(0));
        props.put(Fields.isDefault, Schema.ofBoolean(Fields.isDefault).setDefaultValue(new JsonPrimitive(false)));
        props.put(
                Fields.breakAtFirstMatch,
                Schema.ofBoolean(Fields.breakAtFirstMatch).setDefaultValue(new JsonPrimitive(true)));
        props.put(Fields.isSimple, Schema.ofBoolean(Fields.isSimple).setDefaultValue(new JsonPrimitive(true)));
        props.put(Fields.isComplex, Schema.ofBoolean(Fields.isComplex).setDefaultValue(new JsonPrimitive(false)));
        props.put(
                Fields.userDistributionType,
                Schema.ofString(Fields.userDistributionType)
                        .setEnums(EnumSchemaUtil.getSchemaEnums(DistributionType.class)));
        props.put(Fields.userDistribution, UserDistribution.getSchema());
        props.put(
                Fields.lastAssignedUserId,
                Schema.ofLong(Fields.lastAssignedUserId).setMinimum(1));

        schema.setProperties(props);
        return schema;
    }
}
