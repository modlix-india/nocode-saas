package com.fincity.saas.entity.processor.model.filter;

import com.fincity.saas.entity.processor.util.FilterUtil;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public abstract class BaseProcessorFilter<T extends BaseProcessorFilter<T>> implements Serializable {

    @Serial
    private static final long serialVersionUID = 6742225650221342845L;

    private List<ULong> entityIds;
    private List<ULong> clientIds;
    private List<ULong> assignedUserIds;
    private Boolean isActive;

    public T setEntityIds(List<ULong> entityIds) {
        this.entityIds = entityIds;
        return (T) this;
    }

    public T setClientIds(List<ULong> clientIds) {
        this.clientIds = clientIds;
        return (T) this;
    }

    public T setAssignedUserIds(List<ULong> assignedUserIds) {
        this.assignedUserIds = assignedUserIds;
        return (T) this;
    }

    public T setActive(Boolean isActive) {
        this.isActive = isActive;
        return (T) this;
    }

    public T filterEntityIds(List<ULong> entityIds) {
        this.entityIds = FilterUtil.intersectLists(this.entityIds, entityIds);
        return (T) this;
    }

    public T filterClientIds(List<ULong> clientIds) {
        this.clientIds = FilterUtil.intersectLists(this.clientIds, clientIds);
        return (T) this;
    }

    public T filterAssignedUserIds(List<ULong> assignedUserIds) {
        this.assignedUserIds = FilterUtil.intersectLists(this.assignedUserIds, assignedUserIds);
        return (T) this;
    }

    public T filterActive(Boolean isActive) {
        this.isActive = isActive;
        return (T) this;
    }
}
