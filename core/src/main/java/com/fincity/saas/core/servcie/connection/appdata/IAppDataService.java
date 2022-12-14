package com.fincity.saas.core.servcie.connection.appdata;

import java.util.Map;

import org.springframework.data.domain.Page;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.core.document.Connection;
import com.fincity.saas.core.model.DataObject;

import reactor.core.publisher.Mono;

public interface IAppDataService {

	public Mono<Map<String, Object>> create(Connection conn, DataObject dataObject);

	public Mono<Map<String, Object>> update(Connection conn, DataObject dataObject);

	public Mono<Map<String, Object>> read(Connection conn, String id);

	public Mono<Page<Map<String, Object>>> readPage(Connection conn, AbstractCondition condition);

	public Mono<Boolean> delete(Connection conn, String id);
}
