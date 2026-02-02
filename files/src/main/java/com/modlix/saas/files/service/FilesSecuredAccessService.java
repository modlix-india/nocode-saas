package com.modlix.saas.files.service;

import java.time.LocalDateTime;

import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.jooq.service.AbstractJOOQDataService;
import com.modlix.saas.files.dao.FilesSecuredAccessKeyDao;
import com.modlix.saas.files.dto.FilesSecuredAccessKey;
import com.modlix.saas.files.jooq.tables.records.FilesSecuredAccessKeysRecord;

@Service
public class FilesSecuredAccessService extends
        AbstractJOOQDataService<FilesSecuredAccessKeysRecord, ULong, FilesSecuredAccessKey, FilesSecuredAccessKeyDao> {

    private final FilesMessageResourceService messageResourceService;

    public FilesSecuredAccessService(FilesMessageResourceService messageResourceService) {
        this.messageResourceService = messageResourceService;
    }

    public FilesSecuredAccessKey getAccessRecordByPath(String key) {
        return this.dao.getAccessByKey(key);
    }

    public String getAccessPathByKey(String accessKey) {

        FilesSecuredAccessKey accessKeyObject = this.getAccessRecordByPath(accessKey);

        if (!this.checkAccessWithinTime(accessKeyObject)) {
            return this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                    FilesMessageResourceService.INVALID_KEY);
        }

        if (accessKeyObject.getAccessLimit() == null)
            return accessKeyObject.getPath();

        if (!this.checkAccountCount(accessKeyObject)) {
            return this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                    FilesMessageResourceService.INVALID_KEY);
        }

        boolean hasAccess = this.dao.incrementAccessCount(accessKeyObject.getId());
        if (!hasAccess) {
            return this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                    FilesMessageResourceService.INVALID_KEY);
        }
        return accessKeyObject.getPath();
    }

    private boolean checkAccountCount(FilesSecuredAccessKey accessObject) { // edit here

        return accessObject.getAccessedCount() != null && accessObject.getAccessLimit() != null
                && accessObject.getAccessedCount().intValue() < accessObject.getAccessLimit().intValue();
    }

    private boolean checkAccessWithinTime(FilesSecuredAccessKey accessObject) {

        return LocalDateTime.now().isBefore(accessObject.getAccessTill());
    }

}
