package com.modlix.saas.files.service;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.modlix.saas.commons2.jooq.service.AbstractJOOQUpdatableDataService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;
import com.modlix.saas.files.dao.FilesUploadDownloadDao;
import com.modlix.saas.files.dto.FilesUploadDownloadDTO;
import com.modlix.saas.files.jooq.tables.records.FilesUploadDownloadRecord;

@Service
public class FilesUploadDownloadService extends
        AbstractJOOQUpdatableDataService<FilesUploadDownloadRecord, ULong, FilesUploadDownloadDTO, FilesUploadDownloadDao> {

    @Override
    protected FilesUploadDownloadDTO updatableEntity(FilesUploadDownloadDTO entity) {
        return entity;
    }

    public boolean updateDone(ULong id) {

        return this.dao.updateDone(id);
    }

    @Override
    protected ULong getLoggedInUserId() {
        return ULong.valueOf(SecurityContextUtil.getUsersContextUser().getId());
    }

    public boolean updateFailed(Throwable e, ULong id) {

        return this.dao.updateFailed(e, id);
    }
}
