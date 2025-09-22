package com.fincity.saas.entity.processor.analytics.model;

import com.fincity.saas.entity.processor.model.common.IdAndValue;
import java.io.Serializable;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class StatusCount implements Serializable {

    private IdAndValue<ULong, String> user;

    private Long totalCount;

    private Map<String, Long> statusCounts;

    public static StatusCount of(IdAndValue<ULong, String> user, Long totalCount, Map<String, Long> statusCounts) {
        return new StatusCount().setUser(user).setTotalCount(totalCount).setStatusCounts(statusCounts);
    }

    public static StatusCount of(ULong userId, String userName, Long totalCount, Map<String, Long> statusCounts) {
        return of(IdAndValue.of(userId, userName), totalCount, statusCounts);
    }
}
