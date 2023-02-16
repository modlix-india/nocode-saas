package com.fincity.saas.core.service.connection.appdata;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.util.FlatFileType;
import com.fincity.saas.core.enums.ConnectionSubType;
import com.fincity.saas.core.enums.ConnectionType;
import com.fincity.saas.core.model.DataObject;
import com.fincity.saas.core.service.ConnectionService;
import com.fincity.saas.core.service.StorageService;

import reactor.core.publisher.Mono;

@Service
public class AppDataService {

	private static final ConnectionSubType DEFAULT_APP_DATA_SERVICE = ConnectionSubType.MONGO;

	@Autowired
	private ConnectionService connectionService;

	@Autowired
	private StorageService storageService;

	@Autowired
	private MongoAppDataService mongoAppDataService;

	private EnumMap<ConnectionSubType, IAppDataService> services = new EnumMap<>(ConnectionSubType.class);

	@PostConstruct
	public void init() {

		this.services.putAll(Map.of(ConnectionSubType.MONGO, (IAppDataService) mongoAppDataService));
	}

	public Mono<Map<String, Object>> create(String appCode, String clientCode, String storageName,
	        DataObject dataObject) {

		return FlatMapUtil.flatMapMonoWithNull(

		        () -> connectionService.find(appCode, clientCode, ConnectionType.APP_DATA),

		        conn -> Mono
		                .just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

		        (conn, dataService) -> storageService.read(storageName, appCode, clientCode),

		        (conn, dataService, storage) -> dataService.create(conn, storage, dataObject));
	}

	public Mono<Map<String, Object>> update(String appCode, String clientCode, String storageName,
	        DataObject dataObject, Boolean override) {

		return FlatMapUtil.flatMapMonoWithNull(

		        () -> connectionService.find(appCode, clientCode, ConnectionType.APP_DATA),

		        conn -> Mono
		                .just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

		        (conn, dataService) -> storageService.read(storageName, appCode, clientCode),

		        (conn, dataService, storage) -> dataService.update(conn, storage, dataObject, override));
	}

	public Mono<Map<String, Object>> read(String appCode, String clientCode, String storageName, String id) {
		return FlatMapUtil.flatMapMonoWithNull(

		        () -> connectionService.find(appCode, clientCode, ConnectionType.APP_DATA),

		        conn -> Mono
		                .just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

		        (conn, dataService) -> storageService.read(storageName, appCode, clientCode),

		        (conn, dataService, storage) -> dataService.read(conn, storage, id));
	}

	public Mono<Page<Map<String, Object>>> readPage(String appCode, String clientCode, String storageName,
	        Pageable page, Boolean count, AbstractCondition condition) {
		return FlatMapUtil.flatMapMonoWithNull(

		        () -> connectionService.find(appCode, clientCode, ConnectionType.APP_DATA),

		        conn -> Mono
		                .just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

		        (conn, dataService) -> storageService.read(storageName, appCode, clientCode),

		        (conn, dataService, storage) -> dataService.readPage(conn, storage, page, count, condition));
	}

	public Mono<Boolean> delete(String appCode, String clientCode, String storageName, String id) {
		return FlatMapUtil.flatMapMonoWithNull(

		        () -> connectionService.find(appCode, clientCode, ConnectionType.APP_DATA),

		        conn -> Mono
		                .just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

		        (conn, dataService) -> storageService.read(storageName, appCode, clientCode),

		        (conn, dataService, storage) -> dataService.delete(conn, storage, id));
	}

	public Mono<byte[]> downloadTemplate(String appCode, String clientCode, String storageName, FlatFileType fileType,
	        ServerHttpRequest request, ServerHttpResponse response) {
		return FlatMapUtil.flatMapMonoWithNullLog(

		        () -> connectionService.find(appCode, clientCode, ConnectionType.APP_DATA),

		        conn -> Mono
		                .just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

		        (conn, dataService) -> storageService.read(storageName, appCode, clientCode),

		        (conn, dataService, storage) -> dataService.downloadTemplate(conn, storage, fileType, request, response));
	}
}
