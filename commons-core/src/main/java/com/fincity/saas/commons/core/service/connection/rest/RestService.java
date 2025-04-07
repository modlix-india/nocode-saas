package com.fincity.saas.commons.core.service.connection.rest;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.dao.AbstractCoreTokenDao;
import com.fincity.saas.commons.core.dto.RestRequest;
import com.fincity.saas.commons.core.dto.RestResponse;
import com.fincity.saas.commons.core.enums.ConnectionSubType;
import com.fincity.saas.commons.core.enums.ConnectionType;
import com.fincity.saas.commons.core.service.ConnectionService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import jakarta.annotation.PostConstruct;
import org.jooq.UpdatableRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.EnumMap;

@Service
public abstract class RestService<R extends UpdatableRecord<R>, O extends AbstractCoreTokenDao<R>> {

    private final ConnectionService connectionService;
    private final BasicRestService basicRestService;
    private final OAuth2RestService<R, O> oAuth2RestService;
    private final RestAuthService<R, O> restAuthService;
    private final EnumMap<ConnectionSubType, IRestService> services = new EnumMap<>(ConnectionSubType.class);

    protected RestService(
            ConnectionService connectionService,
            BasicRestService basicRestService,
            OAuth2RestService<R, O> oAuth2RestService,
            RestAuthService<R, O> restAuthService) {
        this.connectionService = connectionService;
        this.basicRestService = basicRestService;
        this.oAuth2RestService = oAuth2RestService;
        this.restAuthService = restAuthService;
    }

    @PostConstruct
    public void init() {
        this.services.put(ConnectionSubType.REST_API_BASIC, basicRestService);
        this.services.put(ConnectionSubType.REST_API_OAUTH2, oAuth2RestService);
        this.services.put(ConnectionSubType.REST_API_AUTH, restAuthService);
    }

    public Mono<RestResponse> doCall(String appCode, String clientCode, String connectionName, RestRequest request) {
        return this.doCall(appCode, clientCode, connectionName, request, false);
    }

    public Mono<RestResponse> doCall(
            String appCode, String clientCode, String connectionName, RestRequest request, boolean fileDownload) {

        return FlatMapUtil.flatMapMono(
                        () -> SecurityContextUtil.resolveAppAndClientCode(appCode, clientCode),
                        codeTuple -> connectionService.read(
                                connectionName, codeTuple.getT1(), codeTuple.getT2(), ConnectionType.REST_API),
                        (codeTuple, connection) -> Mono.just(this.services.get(
                                connection != null
                                        ? connection.getConnectionSubType()
                                        : ConnectionSubType.REST_API_BASIC)),
                        (codeTuple, connection, service) -> service.call(connection, request, fileDownload))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RestService.doCall"))
                .switchIfEmpty(Mono.defer(() -> Mono.just(new RestResponse()
                        .setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .setData("Connection Not found"))));
    }
}
