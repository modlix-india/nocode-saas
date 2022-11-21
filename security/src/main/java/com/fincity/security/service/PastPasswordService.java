package com.fincity.security.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import java.util.List;
import java.util.Objects;

import org.jooq.types.ULong;
import org.jooq.types.UShort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.security.dao.PastPasswordDAO;
import com.fincity.security.dto.PastPassword;
import com.fincity.security.jooq.tables.records.SecurityPastPasswordsRecord;

import reactor.core.publisher.Mono;

@Service
public class PastPasswordService
        extends AbstractJOOQDataService<SecurityPastPasswordsRecord, ULong, PastPassword, PastPasswordDAO> {

	@Autowired
	private ClientService clientService;

	public Mono<UShort> getHistoryCount(ULong clientId) {

		return flatMapMono(

		        () -> clientService.getClientPasswordPolicy(clientId),

		        passwordPolicy -> Mono.just(passwordPolicy.getPassHistoryCount()));

	}

	// delete past password

	public Mono<List<String>> fetchList(ULong userId) {
		return this.dao.fetchPasswordsById(userId);
	}

	public Mono<Integer> getCountForSelectedUser(ULong userId) {
		return this.dao.fetchPasswordsCount(userId);
	}

	public Mono<Boolean> addPassword(PastPassword pastPassword) {
		return this.dao.create(pastPassword)
		        .map(Objects::nonNull);
	}

	// add new password based on history count which was applicable for that
	// selected user id

	public Mono<Boolean> addBasedOnHistoryCount(PastPassword pastPassword) {

		return flatMapMono(

		        SecurityContextUtil::getUsersClientId,

		        clientId -> Mono.just(pastPassword.getUserId()),

		        (clientId, userId) -> this.getCountForSelectedUser(userId),

		        (clientId, userId, pastPasswordCount) -> this.getHistoryCount(ULong.valueOf(clientId)),

		        (clientId, userId, pastPasswordCount, historyCount) ->
				{
			        if (pastPasswordCount.intValue() < historyCount.intValue())
				        return this.dao.create(pastPassword)
				                .map(Objects::nonNull);

			        return deletePastPassword(userId);
		        }

		);

	}

	// delete past password record based on oldest time stamp for selected userId
	public Mono<Boolean> deletePastPassword(ULong userId) {

		return this.dao.deletePastPasswordBasedOnUserId(userId);
	}
}
