package com.fincity.saas.ui.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveFunctionRepository;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.ui.document.UIFunction;
import com.fincity.saas.ui.repository.UIFunctionDocumentRepository;
import com.fincity.saas.ui.service.UIFunctionService;
import com.google.gson.Gson;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuples;

@RestController
@RequestMapping("api/ui/functions")
public class FunctionController
		extends AbstractOverridableDataController<UIFunction, UIFunctionDocumentRepository, UIFunctionService> {
	@GetMapping("/repositoryFind")

	public Mono<ResponseEntity<String>> find(@RequestParam(required = false) String appCode,
			@RequestParam(required = false) String clientCode,
			@RequestParam(required = false, defaultValue = "false") boolean includeKIRunRepos, String namespace,
			String name) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> Mono.just(Tuples.of(CommonsUtil.nonNullValue(appCode, ca.getUrlAppCode()),
						CommonsUtil.nonNullValue(clientCode, ca.getUrlClientCode()))),

				(ca, tup) -> {

					ReactiveRepository<ReactiveFunction> fRepo = (includeKIRunRepos
							? new ReactiveHybridRepository<ReactiveFunction>(new KIRunReactiveFunctionRepository(),
									this.service.getFunctionRepository(tup.getT1(), tup.getT2()))
							: this.service.getFunctionRepository(tup.getT1(), tup.getT2()));

					return fRepo.find(namespace, name);
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
								this.service.getFunctionRepository(tup.getT1(), tup.getT2()))
						: this.service.getFunctionRepository(tup.getT1(), tup.getT2())).filter(filter))
				.collectList()
				.map(ResponseEntity::ok);
	}
}
