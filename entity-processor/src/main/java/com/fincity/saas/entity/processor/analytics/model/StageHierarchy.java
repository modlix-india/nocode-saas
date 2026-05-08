package com.fincity.saas.entity.processor.analytics.model;

import com.fincity.saas.entity.processor.dto.Stage;
import com.fincity.saas.entity.processor.enums.Platform;
import com.fincity.saas.entity.processor.enums.StageType;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
public class StageHierarchy implements Serializable {

    @Serial
    private static final long serialVersionUID = 8172635490182734501L;

    private List<PlatformStages> platforms;

    public static StageHierarchy from(List<Stage> allStages) {

        if (allStages == null || allStages.isEmpty())
            return new StageHierarchy().setPlatforms(List.of());

        Map<Platform, List<Stage>> byPlatform = allStages.stream()
                .collect(Collectors.groupingBy(Stage::getPlatform));

        List<PlatformStages> platforms = new ArrayList<>();

        for (Platform platform : Platform.values()) {
            List<Stage> platformStages = byPlatform.getOrDefault(platform, List.of());
            if (platformStages.isEmpty()) continue;

            platforms.add(new PlatformStages()
                    .setPlatform(platform)
                    .setStageTypes(buildStageTypeGroups(platformStages)));
        }

        return new StageHierarchy().setPlatforms(platforms);
    }

    private static List<StageTypeGroup> buildStageTypeGroups(List<Stage> stages) {

        Map<StageType, List<Stage>> byType = stages.stream()
                .collect(Collectors.groupingBy(Stage::getStageType));

        List<StageTypeGroup> groups = new ArrayList<>();

        for (StageType stageType : StageType.values()) {
            List<Stage> typeStages = byType.getOrDefault(stageType, List.of());
            if (typeStages.isEmpty()) continue;

            List<Stage> parents = typeStages.stream()
                    .filter(s -> Boolean.TRUE.equals(s.getIsParent()))
                    .sorted(Comparator.comparingInt(s -> s.getOrder() != null ? s.getOrder() : Integer.MAX_VALUE))
                    .toList();

            List<Stage> children = typeStages.stream()
                    .filter(s -> Boolean.FALSE.equals(s.getIsParent()))
                    .toList();

            Map<ULong, List<Stage>> childrenByParent = children.stream()
                    .filter(s -> s.getParentLevel0() != null)
                    .collect(Collectors.groupingBy(Stage::getParentLevel0));

            List<ParentStageNode> parentNodes = parents.stream()
                    .map(parent -> {
                        List<Stage> parentChildren =
                                childrenByParent.getOrDefault(parent.getId(), List.of());

                        List<ChildStageNode> childNodes = parentChildren.stream()
                                .sorted(Comparator.comparingInt(
                                        s -> s.getOrder() != null ? s.getOrder() : Integer.MAX_VALUE))
                                .map(child -> new ChildStageNode()
                                        .setId(child.getId())
                                        .setName(child.getName())
                                        .setOrder(child.getOrder())
                                        .setIsSuccess(child.getIsSuccess())
                                        .setIsFailure(child.getIsFailure()))
                                .toList();

                        return new ParentStageNode()
                                .setId(parent.getId())
                                .setName(parent.getName())
                                .setOrder(parent.getOrder())
                                .setIsSuccess(parent.getIsSuccess())
                                .setIsFailure(parent.getIsFailure())
                                .setChildren(childNodes);
                    })
                    .toList();

            groups.add(new StageTypeGroup().setStageType(stageType).setStages(parentNodes));
        }

        return groups;
    }

    @Data
    @Accessors(chain = true)
    public static class PlatformStages implements Serializable {

        @Serial
        private static final long serialVersionUID = 8172635490182734502L;

        private Platform platform;
        private List<StageTypeGroup> stageTypes;
    }

    @Data
    @Accessors(chain = true)
    public static class StageTypeGroup implements Serializable {

        @Serial
        private static final long serialVersionUID = 8172635490182734503L;

        private StageType stageType;
        private List<ParentStageNode> stages;
    }

    @Data
    @Accessors(chain = true)
    public static class ParentStageNode implements Serializable {

        @Serial
        private static final long serialVersionUID = 8172635490182734504L;

        private ULong id;
        private String name;
        private Integer order;
        private Boolean isSuccess;
        private Boolean isFailure;
        private List<ChildStageNode> children;
    }

    @Data
    @Accessors(chain = true)
    public static class ChildStageNode implements Serializable {

        @Serial
        private static final long serialVersionUID = 8172635490182734505L;

        private ULong id;
        private String name;
        private Integer order;
        private Boolean isSuccess;
        private Boolean isFailure;
    }
}
