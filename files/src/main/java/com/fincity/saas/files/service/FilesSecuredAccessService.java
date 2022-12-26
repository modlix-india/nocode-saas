package com.fincity.saas.files.service;

import java.time.LocalDateTime;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.files.dao.FilesSecuredAccessKeyDao;
import com.fincity.saas.files.dto.FilesSecuredAccessKey;
import com.fincity.saas.files.jooq.tables.records.FilesSecuredAccessKeyRecord;

import reactor.core.publisher.Mono;

@Service
public class FilesSecuredAccessService extends
        AbstractJOOQDataService<FilesSecuredAccessKeyRecord, ULong, FilesSecuredAccessKey, FilesSecuredAccessKeyDao> {

	public Mono<FilesSecuredAccessKey> getAccessRecordByPath(String key) {
		return this.dao.getAccessByKey(key);
	}

	public Mono<String> getAccessPathByKey(String accessKey) {

		return FlatMapUtil.flatMapMono(

		        () -> this.getAccessRecordByPath(accessKey),

		        accessKeyObject -> this.checkAccessWithinTime(accessKeyObject)
		                .flatMap(BooleanUtil::safeValueOfWithEmpty),

		        (accessKeyObject, inTime) -> this.checkAccountCount(accessKeyObject)
		                .flatMap(BooleanUtil::safeValueOfWithEmpty),

		        (accessKeyObject, inTime,
		                inCount) -> this.dao
		                        .setAccessCount(accessKeyObject.getId(),
		                                ULongUtil.valueOf(accessKeyObject.getAccessedCount()
		                                        .add(1)))
		                        .flatMap(e -> e.booleanValue() ? Mono.just(accessKeyObject.getPath()) : Mono.empty()));
	}

	private Mono<Boolean> checkAccountCount(FilesSecuredAccessKey accessObject) { // edit here

		return Mono.just(accessObject.getAccessedCount() != null && accessObject.getAccessLimit() != null
		        && accessObject.getAccessedCount()
		                .intValue() < accessObject.getAccessLimit()
		                        .intValue());
	}

	private Mono<Boolean> checkAccessWithinTime(FilesSecuredAccessKey accessObject) {

		return Mono.just(LocalDateTime.now()
		        .isBefore(accessObject.getAccessTill()));
	}

}
