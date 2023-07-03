package com.fincity.saas.core.controller;

import java.util.List;

import javax.annotation.PostConstruct;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveSchemaRepository;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.core.document.CoreSchema;
import com.fincity.saas.core.kirun.repository.CoreSchemaRepository;
import com.fincity.saas.core.repository.CoreSchemaDocumentRepository;
import com.fincity.saas.core.service.CoreSchemaService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuples;

@RestController
@RequestMapping("api/core/schemas")
public class SchemaController
        extends AbstractOverridableDataController<CoreSchema, CoreSchemaDocumentRepository, CoreSchemaService> {

	private CoreSchemaRepository coreSchemaRepo;

	@PostConstruct
	public void init() {

		this.coreSchemaRepo = new CoreSchemaRepository();
	}

	@GetMapping("/repositoryFind")
	public Mono<ResponseEntity<Schema>> find(@RequestParam(required = false) String appCode,
	        @RequestParam(required = false) String clientCode,
	        @RequestParam(required = false, defaultValue = "false") boolean includeKIRunRepos, String namespace,
	        String name) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> Mono.just(Tuples.of(CommonsUtil.nonNullValue(appCode, ca.getUrlAppCode()),
		                CommonsUtil.nonNullValue(clientCode, ca.getUrlClientCode()))),

		        (ca, tup) ->
				{

			        ReactiveRepository<Schema> fRepo = (includeKIRunRepos
			                ? new ReactiveHybridRepository<Schema>(new KIRunReactiveSchemaRepository(),
			                        this.coreSchemaRepo, this.service.getSchemaRepository(tup.getT1(), tup.getT2()))
			                : new ReactiveHybridRepository<Schema>(this.coreSchemaRepo,
			                        this.service.getSchemaRepository(tup.getT1(), tup.getT2())));

			        return fRepo.find(namespace, name);
		        })
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "FunctionController.find"))
		        .map(ResponseEntity::ok);
	}

	@GetMapping("/repositoryFilter")
	public Mono<ResponseEntity<List<String>>> filter(@RequestParam(required = false) String appCode,
	        @RequestParam(required = false) String clientCode,
	        @RequestParam(required = false, defaultValue = "false") boolean includeKIRunRepos, String filter) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> Mono.just(Tuples.of(CommonsUtil.nonNullValue(appCode, ca.getUrlAppCode()),
		                CommonsUtil.nonNullValue(clientCode, ca.getUrlClientCode())))

		)
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "FunctionController.filter"))
		        .flatMapMany(tup ->

				(includeKIRunRepos
				        ? new ReactiveHybridRepository<Schema>(new KIRunReactiveSchemaRepository(), this.coreSchemaRepo,
				                this.service.getSchemaRepository(tup.getT1(), tup.getT2()))
				        : new ReactiveHybridRepository<Schema>(this.coreSchemaRepo,
				                this.service.getSchemaRepository(tup.getT1(), tup.getT2())))
				        .filter(filter))
		        .collectList()
		        .map(ResponseEntity::ok);
	}

}
