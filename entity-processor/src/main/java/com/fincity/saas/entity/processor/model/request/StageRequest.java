package com.fincity.saas.entity.processor.model.request;

import com.fincity.saas.entity.processor.enums.Platform;
import com.fincity.saas.entity.processor.enums.StageType;
import com.fincity.saas.entity.processor.model.base.BaseProductTemplate;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.util.NameUtil;
import java.io.Serial;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class StageRequest extends BaseProductTemplate<StageRequest> {

    @Serial
    private static final long serialVersionUID = 1704140784725077601L;

    private Identity id;
    private Platform platform;
    private StageType stageType;
    private Boolean isSuccess;
    private Boolean isFailure;
    private Integer order;

    public boolean isStageTypeValid() {
        if (stageType == null) return Boolean.FALSE;

        if (stageType.isHasSuccessFailure()) return isSuccess != null || isFailure != null;

        return Boolean.TRUE;
    }

    @Override
    public boolean areChildrenValid() {

        if (super.getChildren() == null || super.getChildren().isEmpty()) return Boolean.TRUE;

        Collection<StageRequest> children = super.getChildren().values();

        if (children.isEmpty()) return Boolean.TRUE;

        Set<String> names = new HashSet<>();

        String parentName = NameUtil.normalize(this.getName());
        for (StageRequest child : children) {
            String name = NameUtil.normalize(child.getName());
            if (name != null && name.equals(parentName)) return Boolean.FALSE;
            if (name != null && !names.add(name)) return Boolean.FALSE;
        }

        return Boolean.TRUE;
    }

    public List<String> getDuplicateChildNames() {
        Collection<StageRequest> children = this.getChildren().values();
        if (children.isEmpty()) return List.of();

        Map<String, Integer> nameCountMap = new HashMap<>();

        for (StageRequest child : children) {
            String name = NameUtil.normalize(child.getName());
            if (name != null) nameCountMap.merge(name, 1, Integer::sum);
        }

        return nameCountMap.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .toList();
    }
}
