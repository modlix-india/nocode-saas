package com.fincity.saas.entity.processor.dto.rule;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.jooq.util.DbSchema;
import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.rule.DistributionType;
import com.fincity.saas.entity.processor.model.common.UserDistribution;
import com.fincity.saas.entity.processor.model.request.rule.RuleInfoRequest;
import com.fincity.saas.entity.processor.model.request.rule.RuleRequest;
import com.fincity.saas.entity.processor.relations.resolvers.field.UserFieldResolver;
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

    @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
    public T setIsDefault(boolean isDefault) {
        this.isDefault = isDefault;
        if (isDefault) this.stageId = null;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T setComplex(boolean isComplex) {
        this.isComplex = isComplex;
        if (isComplex) this.isSimple = false;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T setSimple(boolean isSimple) {
        this.isSimple = isSimple;
        if (isSimple) this.isComplex = false;
        return (T) this;
    }

    @Override
    public void extendSchema(Schema schema) {

        super.extendSchema(schema);

        Map<String, Schema> props = schema.getProperties();

        props.put(Fields.version, DbSchema.ofVersion(Fields.version));
        props.put(Fields.stageId, DbSchema.ofNumberId(Fields.stageId));
        props.put(Fields.order, Schema.ofNumber(Fields.order).setMinimum(0));
        props.put(Fields.isDefault, DbSchema.ofBooleanFalse(Fields.isDefault));
        props.put(Fields.breakAtFirstMatch, DbSchema.ofBooleanTrue(Fields.breakAtFirstMatch));
        props.put(Fields.isSimple, DbSchema.ofBooleanTrue(Fields.isSimple));
        props.put(Fields.isComplex, DbSchema.ofBooleanFalse(Fields.isComplex));
        props.put(Fields.userDistributionType, DbSchema.ofEnum(Fields.userDistributionType, DistributionType.class));
        props.put(Fields.userDistribution, UserDistribution.getSchema());
        props.put(Fields.lastAssignedUserId, DbSchema.ofNumberId(Fields.lastAssignedUserId));

        schema.setProperties(props);
    }
}
