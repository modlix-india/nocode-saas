package com.fincity.saas.entity.processor.model.request;

import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.common.Identity;
import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    private List<IdAndValue<Identity, Integer>> stageOrders;

    public boolean isValidOrder() {
        boolean allId = true;
        boolean allCode = true;

        Set<Identity> identitySet = new HashSet<>();
        Set<Integer> orderValueSet = new HashSet<>();

        for (IdAndValue<Identity, Integer> entry : stageOrders) {
            Identity identity = entry.getId();
            Integer value = entry.getValue();

            if (identity == null || value == null || value < 0) return false;

            allId &= identity.isId();
            allCode &= identity.isCode();

            if (!identitySet.add(identity) || !orderValueSet.add(value)) return false;
        }

        return allId || allCode;
    }
}
