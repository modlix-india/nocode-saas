package com.fincity.saas.core.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Service
public class DataService {

	@Autowired
	private ConnectionService connectionService;
	
	public Mono<Map<String, Object>> create(String appCode, String clientCode, Map<String, Object> entity) {
		
		connectionService.find(appCode, clientCode, "appData");
	}
}
