package com.fincity.saas.data.controller;

import org.jooq.types.ULong;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.saas.data.dao.StorageDAO;
import com.fincity.saas.data.dto.Storage;
import com.fincity.saas.data.jooq.tables.records.DataStorageRecord;
import com.fincity.saas.data.service.StorageService;

@RestController
@RequestMapping("/api/data/storage")
public class StorageController
        extends AbstractJOOQUpdatableDataController<DataStorageRecord, ULong, Storage, StorageDAO, StorageService> {

}
