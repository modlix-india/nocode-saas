package com.fincity.saas.entity.processor.model.response;

import com.fincity.saas.entity.processor.model.common.BaseValue;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BaseValueResponse {
    private BaseValue parent;
    private List<BaseValue> child;

    public static List<BaseValueResponse> toList(Map<BaseValue, Set<BaseValue>> valueMap) {
        return valueMap.entrySet().stream()
                .map(entry -> new BaseValueResponse(
                        entry.getKey(), entry.getValue().stream().toList()))
                .toList();
    }

    public static List<BaseValueResponse> toList(NavigableMap<BaseValue, NavigableSet<BaseValue>> valueMap) {
        return valueMap.entrySet().stream()
                .map(entry -> new BaseValueResponse(
                        entry.getKey(), entry.getValue().stream().toList()))
                .toList();
    }
}
