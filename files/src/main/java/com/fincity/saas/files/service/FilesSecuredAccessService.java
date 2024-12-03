package com.fincity.saas.files.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.*;

import java.time.LocalDateTime;

import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.files.dao.FilesSecuredAccessKeyDao;
import com.fincity.saas.files.dto.FilesSecuredAccessKey;
import com.fincity.saas.files.jooq.tables.records.FilesSecuredAccessKeysRecord;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class FilesSecuredAccessService extends
		AbstractJOOQDataService<FilesSecuredAccessKeysRecord, ULong, FilesSecuredAccessKey, FilesSecuredAccessKeyDao> {

	private final FilesMessageResourceService messageResourceService;

	public FilesSecuredAccessService(FilesMessageResourceService messageResourceService) {
		this.messageResourceService = messageResourceService;
	}

	public Mono<FilesSecuredAccessKey> getAccessRecordByPath(String key) {
		return this.dao.getAccessByKey(key);
	}

	public Mono<String> getAccessPathByKey(String accessKey) {

		return flatMapMono(

				() -> this.getAccessRecordByPath(accessKey),

				accessKeyObject -> this.checkAccessWithinTime(accessKeyObject)
						.flatMap(BooleanUtil::safeValueOfWithEmpty),

				(accessKeyObject, inTime) -> StringUtil.safeIsBlank(accessKeyObject.getAccessLimit())
						? Mono.just(accessKeyObject.getPath())
						: flatMapMono(

								() -> this.checkAccountCount(accessKeyObject)
										.flatMap(BooleanUtil::safeValueOfWithEmpty),

								inCount -> this.dao.incrementAccessCount(accessKeyObject.getId())
										.flatMap(hasAccess -> {
											if (BooleanUtil.safeValueOf(hasAccess))
												return Mono.just(accessKeyObject.getPath());
											return Mono.empty();
										})
										.contextWrite(Context.of(LogUtil.METHOD_NAME,
												"FilesSecuredAccessService.getAccessPathByKey"))

						))
				.switchIfEmpty(
						this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
								FilesMessageResourceService.INVALID_KEY))

				.contextWrite(Context.of(LogUtil.METHOD_NAME, "FilesSecuredAccessService.getAccessPathByKey"));
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
