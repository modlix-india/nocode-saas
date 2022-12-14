package com.fincity.saas.core.servcie.connection.appdata;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.core.document.Connection;
import com.fincity.saas.core.model.DataObject;

import reactor.core.publisher.Mono;

@Service
public class MongoAppDataService implements IAppDataService {

	@Override
	public Mono<Map<String, Object>> create(Connection conn, DataObject dataObject) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Mono<Map<String, Object>> update(Connection conn, DataObject dataObject) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Mono<Map<String, Object>> read(Connection conn, String id) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Mono<Page<Map<String, Object>>> readPage(Connection conn, AbstractCondition condition) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Mono<Boolean> delete(Connection conn, String id) {
		// TODO Auto-generated method stub
		return null;
	}
	
}
