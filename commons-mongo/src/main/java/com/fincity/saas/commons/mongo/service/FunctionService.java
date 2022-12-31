package com.fincity.saas.commons.mongo.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.Repository;
import com.fincity.nocode.kirun.engine.function.AbstractFunction;
import com.fincity.nocode.kirun.engine.model.FunctionDefinition;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.runtime.FunctionExecutionParameters;
import com.fincity.nocode.kirun.engine.runtime.KIRuntime;
import com.fincity.saas.commons.mongo.document.Function;
import com.fincity.saas.commons.mongo.repository.FunctionRepository;
import com.fincity.saas.commons.util.StringUtil;

import reactor.core.publisher.Mono;

@Service
public class FunctionService extends AbstractOverridableDataServcie<Function, FunctionRepository> {

	private static final String NAMESPACE = "namespace";
	private static final String NAME = "name";

	private static final String CACHE_NAME_FUNCTION_REPO = "functionRepo";

	private Map<String, Repository<com.fincity.nocode.kirun.engine.function.Function>> functions = new HashMap<>();
	
	public FunctionService() {
		super(Function.class);
	}

	@Override
	public Mono<Function> create(Function entity) {

		String name = StringUtil.safeValueOf(entity.getDefinition()
		        .get(NAME));
		String namespace = StringUtil.safeValueOf(entity.getDefinition()
		        .get(NAMESPACE));

		if (name == null || namespace == null) {
			return this.messageResourceService.throwMessage(HttpStatus.BAD_REQUEST,
			        AbstractMongoMessageResourceService.FUNCTION_NAME_MISSING);
		}

		entity.setName(namespace + "." + name);

		return super.create(entity);
	}

	@Override
	protected Mono<Function> updatableEntity(Function entity) {

		return flatMapMono(

		        () -> this.read(entity.getId()),

		        existing ->
				{
			        if (existing.getVersion() != entity.getVersion())
				        return this.messageResourceService.throwMessage(HttpStatus.PRECONDITION_FAILED,
				                AbstractMongoMessageResourceService.VERSION_MISMATCH);

			        String name = StringUtil.safeValueOf(entity.getDefinition()
			                .get(NAME));
			        String namespace = StringUtil.safeValueOf(entity.getDefinition()
			                .get(NAMESPACE));

			        if (name == null || namespace == null) {
				        return this.messageResourceService.throwMessage(HttpStatus.BAD_REQUEST,
				                AbstractMongoMessageResourceService.FUNCTION_NAME_MISSING);
			        }

			        String funName = namespace + "." + name;

			        if (!funName.equals(existing.getName())) {

				        return this.messageResourceService.throwMessage(HttpStatus.BAD_REQUEST,
				                AbstractMongoMessageResourceService.FUNCTION_NAME_CHANGE);
			        }

			        existing.setDefinition(entity.getDefinition());
			        existing.setVersion(existing.getVersion() + 1);

			        return Mono.just(existing);
		        });
	}

	public Repository<com.fincity.nocode.kirun.engine.function.Function> getFunctionRepository(String appCode,
	        String clientCode) {

		return functions.computeIfAbsent(appCode + " - " + clientCode, key -> (namespace, name) -> {
			String fnName = StringUtil.safeIsBlank(namespace) ? name : namespace + "." + name;
			return cacheService
			        .cacheValueOrGet(CACHE_NAME_FUNCTION_REPO, () -> read(fnName, appCode, clientCode), appCode,
			                clientCode, fnName)
			        .map(s ->
					{

				        FunctionDefinition fd = objectMapper.convertValue(s.getDefinition(), FunctionDefinition.class);

				        return new AbstractFunction() {

					        @Override
					        public FunctionSignature getSignature() {

						        return fd;
					        }

					        @Override
					        protected FunctionOutput internalExecute(FunctionExecutionParameters context) {

						        KIRuntime runtime = new KIRuntime(fd);
						        return runtime.execute(context);
					        }
				        };
			        })
			        .block();
		});
	}
}
