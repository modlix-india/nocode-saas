package com.fincity.saas.core.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.saas.core.model.DataObject;

import reactor.core.publisher.Mono;

@Service
public class DataService {

	@Autowired
	private ConnectionService connectionService;

	public Mono<Map<String, Object>> create(String appCode, String clientCode, DataObject dataObject) {

		return FlatMapUtil.flatMapMonoWithNull(
				
				() -> connectionService.find(appCode, clientCode, ConnectionType.APP_DATA),
				
				
				);
	}
}
