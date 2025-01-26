package com.fincity.saas.files.service;

import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.files.dao.FilesUploadDownloadDao;
import com.fincity.saas.files.dto.FilesUploadDownloadDTO;
import com.fincity.saas.files.jooq.tables.records.FilesUploadDownloadRecord;

import reactor.core.publisher.Mono;

@Service
public class FilesUploadDownloadService extends
        AbstractJOOQUpdatableDataService<FilesUploadDownloadRecord, ULong, FilesUploadDownloadDTO, FilesUploadDownloadDao> {

    @Override
    protected Mono<FilesUploadDownloadDTO> updatableEntity(FilesUploadDownloadDTO entity) {

        return Mono.just(entity);
    }

    @Override
    protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

        return Mono.just(fields);
    }

    public Mono<Boolean> updateDone(ULong id) {

        return this.dao.updateDone(id);
    }

    @Override
    protected Mono<ULong> getLoggedInUserId() {
        return SecurityContextUtil.getUsersContextAuthentication().map(ContextAuthentication::getUser)
                .map(ContextUser::getId).map(ULong::valueOf);
    }

    public Mono<Boolean> updateFailed(Throwable e, ULong id) {

        return this.dao.updateFailed(e, id);
    }
}
