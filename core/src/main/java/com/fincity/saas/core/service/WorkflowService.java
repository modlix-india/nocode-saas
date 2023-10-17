package com.fincity.saas.core.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.core.document.Workflow;
import com.fincity.saas.core.repository.WorkflowRepository;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class WorkflowService extends AbstractOverridableDataService<Workflow, WorkflowRepository> {

	protected WorkflowService() {
		super(Workflow.class);
	}

	@Override
	public Mono<Workflow> create(Workflow entity) {

		if (entity.getTrigger() == null) {
			return this.messageResourceService.throwMessage(
			        msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
			        CoreMessageResourceService.WORKFLOW_TRIGGER_MISSING);
		}

		return super.create(entity).flatMap(this::addSchedularForTriggers);
	}

	@Override
	protected Mono<Workflow> updatableEntity(Workflow entity) {

		return flatMapMono(

		        () -> this.read(entity.getId()),

		        existing ->
				{
			        if (existing.getVersion() != entity.getVersion())
				        return this.messageResourceService.throwMessage(
				                msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
				                AbstractMongoMessageResourceService.VERSION_MISMATCH);

			        if (entity.getTrigger() == null) {
				        return this.messageResourceService.throwMessage(
				                msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
				                CoreMessageResourceService.WORKFLOW_TRIGGER_MISSING);
			        }

			        existing.setStartAuth(entity.getStartAuth());
			        existing.setSteps(entity.getSteps());
			        existing.setVersion(existing.getVersion() + 1);

			        if (!entity.getTrigger()
			                .equals(existing.getTrigger())) {

				        existing.setTrigger(entity.getTrigger());
				        return this.addSchedularForTriggers(existing);
			        }

			        return Mono.just(existing);
		        }).contextWrite(Context.of(LogUtil.METHOD_NAME, "WorkflowService.updatableEntity"));
	}

	private Mono<Workflow> addSchedularForTriggers(Workflow entity) {

		// TODO: Need to write logic to add triggers.

		return Mono.just(entity);
	}

	private Mono<Boolean> removeSchedularForTriggers(Workflow entity) {

		// TODO: Need to write logic to remove triggers.

		return Mono.just(true);
	}

	@Override
	public Mono<Boolean> delete(String id) {

		return flatMapMono(

		        () -> this.read(id),

		        this::removeSchedularForTriggers,

		        (entity, removed) -> super.delete(id))
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "WorkflowService.delete"));
	}

}
