package com.fincity.saas.core.service.connection.appdata;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.core.document.Connection;
import com.fincity.saas.core.document.Storage;
import com.fincity.saas.core.model.DataObject;
import com.google.gson.JsonObject;

import reactor.core.publisher.Mono;

public interface IAppDataService {

	public Mono<Map<String, Object>> create(Connection conn, Storage storage, DataObject dataObject);

	public Mono<Map<String, Object>> update(Connection conn, Storage storage, DataObject dataObject, Boolean override);

	public Mono<Map<String, Object>> read(Connection conn, Storage storage, String id);

	public Mono<Page<Map<String, Object>>> readPage(Connection conn, Storage storage, Pageable page, Boolean count,
	        AbstractCondition condition);

	public Mono<Boolean> delete(Connection conn, Storage storage, String id);

	public Mono<Map<String, Object>> bulkCreate(Connection conn, Storage storage, JsonObject jsonObjectList);
}
