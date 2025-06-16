package com.fincity.saas.entity.processor.dto;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.Map;

import org.jooq.types.ULong;

import com.fincity.saas.entity.processor.dto.base.BaseDto;
import com.fincity.saas.entity.processor.enums.ActivityAction;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.model.common.ActivityObject;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class Activity extends BaseDto<Activity> {

    @Serial
    private static final long serialVersionUID = 4705602267455594423L;

    private String descriptionCode;
    private ULong ticketId;
    private LocalDateTime activityDate;
    private ActivityAction activityAction;
    private ULong actorId;
    private EntitySeries objectEntitySeries;
    private ULong objectId;
    private Map<String, Object> objectData;

    public static Activity of(ULong ticketId, ActivityAction action, ActivityObject object) {
        return new Activity()
                .setTicketId(ticketId)
                .setActivityAction(action)
                .setObjectEntitySeries(object.getEntitySeries())
                .setObjectId(object.getId())
                .setObjectData(object.getData());
    }
}
