package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.entity.processor.dto.base.BaseDto;
import com.fincity.saas.entity.processor.enums.ActivityAction;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.model.common.ActivityObject;
import com.fincity.saas.entity.processor.eager.relations.resolvers.field.UserFieldResolver;
import java.io.Serial;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class Activity extends BaseDto<Activity> {

    @Serial
    private static final long serialVersionUID = 4705602267455594423L;

    private ULong ticketId;
    private ULong taskId;
    private ULong noteId;
    private String comment;
    private LocalDateTime activityDate;
    private ActivityAction activityAction;
    private ULong actorId;
    private EntitySeries objectEntitySeries;
    private ULong objectId;
    private Map<String, Object> objectData;

    public Activity() {
        super();
        this.relationsMap.put(Fields.taskId, EntitySeries.TASK.getTable());
        this.relationsMap.put(Fields.noteId, EntitySeries.NOTE.getTable());
        this.relationsResolverMap.put(UserFieldResolver.class, Fields.actorId);
    }

    public Activity(Activity activity) {
        super(activity);
        this.ticketId = activity.ticketId;
        this.taskId = activity.taskId;
        this.noteId = activity.noteId;
        this.comment = activity.comment;
        this.activityDate = activity.activityDate;
        this.activityAction = activity.activityAction;
        this.actorId = activity.actorId;
        this.objectEntitySeries = activity.objectEntitySeries;
        this.objectId = activity.objectId;
        this.objectData = CloneUtil.cloneMapObject(activity.objectData);
    }

    public static Activity of(ULong ticketId, ActivityAction action, ActivityObject object) {
        return new Activity()
                .setTicketId(ticketId)
                .setActivityAction(action)
                .setComment(object.getComment())
                .setObjectEntitySeries(object.getEntitySeries())
                .setObjectId(object.getId())
                .setObjectData(object.getData());
    }
}
