package com.fincity.saas.commons.mongo.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.array.ArraySchemaType;
import com.fincity.nocode.kirun.engine.json.schema.array.ArraySchemaType.ArraySchemaTypeAdapter;
import com.fincity.nocode.kirun.engine.json.schema.object.AdditionalType;
import com.fincity.nocode.kirun.engine.json.schema.object.AdditionalType.AdditionalTypeAdapter;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.nocode.kirun.engine.json.schema.type.Type.SchemaTypeAdapter;
import com.fincity.nocode.kirun.engine.model.FunctionDefinition;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.mongo.document.AbstractFunction;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public abstract class AbstractFunctionService<D extends AbstractFunction<D>, R extends IOverridableDataRepository<D>>
        extends AbstractOverridableDataService<D, R> {

	protected AbstractFunctionService(Class<D> pojoClass) {
		super(pojoClass);
	}

	private static final String NAMESPACE = "namespace";
	private static final String NAME = "name";

	private static final String CACHE_NAME_FUNCTION_REPO = "functionRepo";

	private Map<String, ReactiveRepository<ReactiveFunction>> functions = new HashMap<>();

	@Override
	public Mono<D> create(D entity) {

		String name = StringUtil.safeValueOf(entity.getDefinition()
		        .get(NAME));
		String namespace = StringUtil.safeValueOf(entity.getDefinition()
		        .get(NAMESPACE));

		if (name == null || namespace == null) {
			return this.messageResourceService.throwMessage(HttpStatus.BAD_REQUEST,
			        AbstractMongoMessageResourceService.NAME_MISSING);
		}

		entity.setName(namespace + "." + name);

		return super.create(entity);
	}

	@Override
	protected Mono<D> updatableEntity(D entity) {

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
				                AbstractMongoMessageResourceService.NAME_MISSING);
			        }

			        String funName = namespace + "." + name;

			        if (!funName.equals(existing.getName())) {

				        return this.messageResourceService.throwMessage(HttpStatus.BAD_REQUEST,
				                AbstractMongoMessageResourceService.NAME_CHANGE);
			        }

			        existing.setDefinition(entity.getDefinition());
			        existing.setVersion(existing.getVersion() + 1)
			                .setPermission(entity.getPermission());

			        return Mono.just(existing);
		        }).contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFunctionService.updatableEntity"));
	}

	public ReactiveRepository<ReactiveFunction> getFunctionRepository(String appCode, String clientCode) {

		return functions.computeIfAbsent(appCode + " - " + clientCode,

		        key ->

				new ReactiveRepository<ReactiveFunction>() {

					public Mono<ReactiveFunction> find(String namespace, String name) {

						String fnName = StringUtil.safeIsBlank(namespace) ? name : namespace + "." + name;

						return FlatMapUtil.flatMapMono(

						        () -> cacheService.cacheValueOrGet(CACHE_NAME_FUNCTION_REPO,
						                () -> read(fnName, appCode, clientCode), appCode, clientCode, fnName),

						        s ->
								{

							        ArraySchemaTypeAdapter arraySchemaTypeAdapter = new ArraySchemaTypeAdapter();

							        AdditionalTypeAdapter additionalTypeAdapter = new AdditionalTypeAdapter();

							        Gson gson = new GsonBuilder()
							                .registerTypeAdapter(Type.class, new SchemaTypeAdapter())
							                .registerTypeAdapter(AdditionalType.class, additionalTypeAdapter)
							                .registerTypeAdapter(ArraySchemaType.class, arraySchemaTypeAdapter)
							                .create();

							        arraySchemaTypeAdapter.setGson(gson);

							        additionalTypeAdapter.setGson(gson);

							        FunctionDefinition fd = gson.fromJson(gson.toJsonTree(s.getDefinition()),
							                FunctionDefinition.class);

							        return Mono.just((ReactiveFunction) new DefinitionFunction(fd, s.getExecuteAuth()));
						        })
						        .contextWrite(Context.of(LogUtil.METHOD_NAME,
						                "AbstractFunctionService.getFunctionRepository"));

					}

					public Flux<String> filter(String name) {

						return Flux.empty();

					}
				});
	}
}
