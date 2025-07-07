package com.fincity.saas.entity.processor.model.request;

import com.fincity.saas.entity.processor.model.common.Identity;
import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class StageReorderRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1704140784725077603L;

    private Identity productTemplateId;

    private Map<Identity, Integer> stageOrders;

    public boolean isValidOrder() {
        if (stageOrders == null || stageOrders.isEmpty()) return false;

        long uniqueCount = stageOrders.values().stream()
                .distinct()
                .filter(value -> value >= 0)
                .count();

        return uniqueCount == stageOrders.size();
    }
}
