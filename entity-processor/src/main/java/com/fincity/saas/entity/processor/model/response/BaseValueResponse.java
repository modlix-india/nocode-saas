package com.fincity.saas.entity.processor.model.response;

import com.fincity.saas.entity.processor.dto.base.BaseValueDto;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BaseValueResponse<T extends BaseValueDto<T>> {

    private T parent;
    private List<T> child;

    public static <T extends BaseValueDto<T>> List<BaseValueResponse<T>> toList(Map<T, ? extends Set<T>> valueMap) {
        return valueMap.entrySet().stream()
                .map(entry -> new BaseValueResponse<>(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
    }
}
