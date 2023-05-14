package com.fincity.saas.core.document;

import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.util.CloneUtil;
import com.fincity.saas.core.model.TriggerWorkflowStep;
import com.fincity.saas.core.model.WorkflowStep;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "workflowFilteringIndex")
@Accessors(chain = true)
@NoArgsConstructor
public class Workflow extends AbstractOverridableDTO<Workflow> {

	private static final long serialVersionUID = -7863306840732076558L;

	private Boolean isTemplate;
	private String startAuth;
	private Map<String, WorkflowStep> steps;
	private TriggerWorkflowStep trigger;

	public Workflow(Workflow wf) {

		super(wf);
		this.isTemplate = wf.isTemplate;
		this.startAuth = wf.startAuth;
		this.steps = CloneUtil.cloneMapObject(wf.steps);
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
