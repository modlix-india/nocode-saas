package com.fincity.saas.core.controller;

import java.util.List;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveFunctionRepository;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.core.document.CoreFunction;
import com.fincity.saas.core.kirun.repository.CoreFunctionRepository;
import com.fincity.saas.core.repository.CoreFunctionDocumentRepository;
import com.fincity.saas.core.service.CoreFunctionService;
import com.fincity.saas.core.service.connection.appdata.AppDataService;
import com.google.gson.Gson;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuples;

@RestController
@RequestMapping("api/core/functions")
public class FunctionController
        extends AbstractOverridableDataController<CoreFunction, CoreFunctionDocumentRepository, CoreFunctionService> {

	@Autowired
	private AppDataService appDataService;

	@Autowired
	private ObjectMapper objectMapper;

	private CoreFunctionRepository coreFunRepo;

	@PostConstruct
	public void init() {

		this.coreFunRepo = new CoreFunctionRepository(appDataService, objectMapper);
	}

	@GetMapping("/repositoryFind")
	public Mono<ResponseEntity<String>> find(@RequestParam(required = false) String appCode,
	        @RequestParam(required = false) String clientCode,
	        @RequestParam(required = false, defaultValue = "false") boolean includeKIRunRepos, String namespace,
	        String name) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> Mono.just(Tuples.of(CommonsUtil.nonNullValue(appCode, ca.getUrlAppCode()),
		                CommonsUtil.nonNullValue(clientCode, ca.getUrlClientCode()))),

		        (ca, tup) ->
				{

			        ReactiveRepository<ReactiveFunction> fRepo = (includeKIRunRepos
			                ? new ReactiveHybridRepository<ReactiveFunction>(new KIRunReactiveFunctionRepository(),
			                        this.coreFunRepo, this.service.getFunctionRepository(tup.getT1(), tup.getT2()))
			                : new ReactiveHybridRepository<ReactiveFunction>(this.coreFunRepo,
			                        this.service.getFunctionRepository(tup.getT1(), tup.getT2())));

			        return fRepo.find(namespace, name)
			                .map(e ->
							{

				                if (!(e instanceof DefinitionFunction))
					                return e;

				                return ((DefinitionFunction) e).getOnlySignatureFromDefinitionAsFunction();
			                });
		        },

		        (ca, tup, fun) -> Mono.just((new Gson()).toJson(fun)))
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "FunctionController.find"))
		        .map(str -> ResponseEntity.ok()
		                .contentType(MediaType.APPLICATION_JSON)
		                .body(str));
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
				        ? new ReactiveHybridRepository<ReactiveFunction>(new KIRunReactiveFunctionRepository(),
				                this.coreFunRepo, this.service.getFunctionRepository(tup.getT1(), tup.getT2()))
				        : new ReactiveHybridRepository<ReactiveFunction>(this.coreFunRepo,
				                this.service.getFunctionRepository(tup.getT1(), tup.getT2())))
				        .filter(filter))
		        .collectList()
		        .map(ResponseEntity::ok);
	}
}
