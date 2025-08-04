package com.fincity.saas.entity.processor.analytics.model;

import java.util.Map;

import org.jooq.types.ULong;

import com.fincity.saas.entity.processor.analytics.util.MathUtil;
import com.fincity.saas.entity.processor.model.common.IdAndValue;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class StatusCount {

    private IdAndValue<ULong, String> user;

    private Long totalCount;

    private Map<String, Long> statusCounts;

    public static StatusCount of(IdAndValue<ULong, String> user, Map<String, Long> statusCounts) {
        return new StatusCount().setUser(user).setStatusCounts(statusCounts);
    }

    public static StatusCount of(ULong userId, String userName, Map<String, Long> statusCounts) {
        return of(IdAndValue.of(userId, userName), statusCounts);
    }

    public StatusCount setStatusCounts(Map<String, Long> statusCounts) {
        this.statusCounts = statusCounts;
        this.totalCount = MathUtil.sumMapValues(statusCounts);
        return this;
    }
}
