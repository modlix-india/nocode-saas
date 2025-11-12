package com.modlix.saas.files.controller;

import org.jooq.types.ULong;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.modlix.saas.commons2.jooq.controller.AbstractJOOQUpdatableDataController;
import com.modlix.saas.files.dao.FilesAccessPathDao;
import com.modlix.saas.files.dto.FilesAccessPath;
import com.modlix.saas.files.jooq.tables.records.FilesAccessPathRecord;
import com.modlix.saas.files.service.FilesAccessPathService;

@RestController
@RequestMapping("api/files/accesspath")
public class FilesAccessPathController extends
        AbstractJOOQUpdatableDataController<FilesAccessPathRecord, ULong, FilesAccessPath, FilesAccessPathDao, FilesAccessPathService> {

}
