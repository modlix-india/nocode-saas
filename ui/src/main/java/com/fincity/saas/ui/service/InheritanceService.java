package com.fincity.saas.ui.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMonoWithNull;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.ui.document.Application;
import com.fincity.saas.ui.repository.ApplicationRepository;

import reactor.core.publisher.Mono;

@Service
public class InheritanceService {
	
	private static final String CACHE_NAME_INHERITANCE_ORDER = "inheritanceOrder";
	
	@Autowired
	private ApplicationRepository repo;
	
	@Autowired
	private CacheService cacheService;

	@SuppressWarnings("unchecked")
	protected Mono<List<String>> order(String appName, String clientCode) {

		return flatMapMonoWithNull(

		        () -> cacheService.makeKey(appName, "-", clientCode),

		        key -> cacheService.get(CACHE_NAME_INHERITANCE_ORDER, key)
		                .map(e -> (List<String>) e),

		        (key, cList) ->
				{
			        if (cList != null)
				        return Mono.just(cList);

			        return this.repo.findOneByNameAndAppCodeAndClientCode(appName, appName, clientCode)
			                .expandDeep(e -> e.getBaseClientCode() == null ? Mono.empty()
			                        : this.repo.findOneByNameAndAppCodeAndClientCode(e.getName(),
			                                e.getAppCode(), e.getBaseClientCode()))
			                .map(Application::getClientCode)
			                .collectList();
		        },

		        (key, cList, finList) ->
				{

			        if (cList == null && finList != null) {
				        cacheService.put(CACHE_NAME_INHERITANCE_ORDER, finList, key);
			        }

			        return Mono.just(finList);
		        });
	}
}
