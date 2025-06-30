package com.fincity.saas.entity.processor.model.response;

import com.fincity.saas.entity.processor.dto.base.BaseValueDto;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jooq.types.ULong;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BaseValueResponse<T extends BaseValueDto<T>> {

    private T parent;
    private List<T> child;


    public static <T extends BaseValueDto<T>> List<BaseValueResponse<T>> toList(
            Map<T, Set<T>> valueMap, ULong... parentId) {
        return valueMap.entrySet().stream()
                .filter(entry -> parentId == null
                        || parentId.length == 0
                        || List.of(parentId).contains(entry.getKey().getId()))
                .map(entry -> new BaseValueResponse<>(
                        entry.getKey(), entry.getValue().stream().toList()))
                .toList();
    }

    public static <T extends BaseValueDto<T>> List<BaseValueResponse<T>> toList(
            NavigableMap<T, NavigableSet<T>> valueMap, ULong... parentId) {
        return valueMap.entrySet().stream()
                .filter(entry -> parentId == null
                        || parentId.length == 0
                        || List.of(parentId).contains(entry.getKey().getId()))
                .map(entry -> new BaseValueResponse<>(
                        entry.getKey(), entry.getValue().stream().toList()))
                .toList();
    }
}
