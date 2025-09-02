package com.fincity.saas.commons.core.document;

import com.fincity.saas.commons.core.model.TriggerWorkflowStep;
import com.fincity.saas.commons.core.model.WorkflowStep;
import com.fincity.saas.commons.model.dto.AbstractOverridableDTO;
import com.fincity.saas.commons.util.CloneUtil;
import java.io.Serial;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "workflowFilteringIndex")
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public class Workflow extends AbstractOverridableDTO<Workflow> {

    @Serial
    private static final long serialVersionUID = -7863306840732076558L;

    private Boolean isTemplate;
    private String startAuth;
    private Map<String, WorkflowStep> steps;
    private TriggerWorkflowStep trigger;

    public Workflow(Workflow workflow) {
        super(workflow);
        this.isTemplate = workflow.isTemplate;
        this.startAuth = workflow.startAuth;
        this.steps = CloneUtil.cloneMapObject(workflow.steps);
        this.trigger = new TriggerWorkflowStep(trigger);
    }

    @Override
    public Mono<Workflow> applyOverride(Workflow base) {
        return Mono.just(this);
    }

    @Override
    public Mono<Workflow> makeOverride(Workflow base) {
        return Mono.just(this);
    }
}
