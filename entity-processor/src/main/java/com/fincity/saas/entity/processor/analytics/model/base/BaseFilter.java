package com.fincity.saas.entity.processor.analytics.model.base;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.util.FilterUtil;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@ToString(callSuper = true)
@FieldNameConstants
public class BaseFilter<T extends BaseFilter<T>> implements Serializable {

    @Serial
    private static final long serialVersionUID = 5132180322339375704L;

    private List<ULong> createdByIds;
    private List<ULong> assignedUserIds;
    private List<ULong> clientIds;

    private boolean includeZero;
    private boolean includePercentage;
    private boolean includeTotal;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    @JsonIgnore
    private BaseFieldData baseFieldData = new BaseFieldData();

    public T filterCreatedByIds(List<ULong> createdByIds) {
        this.createdByIds = FilterUtil.intersectLists(this.createdByIds, createdByIds);
        return (T) this;
    }

    public T filterAssignedUserIds(List<ULong> assignedUserIds) {
        this.assignedUserIds = FilterUtil.intersectLists(this.assignedUserIds, assignedUserIds);
        return (T) this;
    }

    public T filterClientIds(List<ULong> clientIds) {
        this.clientIds = FilterUtil.intersectLists(this.clientIds, clientIds);
        return (T) this;
    }

    public T setCreatedBys(List<IdAndValue<ULong, String>> createdBys) {
        this.baseFieldData.setCreatedBys(createdBys);
        return (T) this;
    }

    public T setAssignedUsers(List<IdAndValue<ULong, String>> assignedUsers) {
        this.baseFieldData.setAssignedUsers(assignedUsers);
        return (T) this;
    }

    public T setClients(List<IdAndValue<ULong, String>> clients) {
        this.baseFieldData.setClients(clients);
        return (T) this;
    }

    @Data
    @Accessors(chain = true)
    @ToString(callSuper = true)
    @FieldNameConstants
    public static class BaseFieldData implements Serializable {

        @Serial
        private static final long serialVersionUID = 1408689325675518972L;

        private List<IdAndValue<ULong, String>> createdBys;
        private List<IdAndValue<ULong, String>> assignedUsers;
        private List<IdAndValue<ULong, String>> clients;
    }
}
