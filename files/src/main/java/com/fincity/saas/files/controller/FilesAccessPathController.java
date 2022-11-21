package com.fincity.saas.files.controller;

import org.jooq.types.ULong;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.saas.files.dao.FilesAccessPathDao;
import com.fincity.saas.files.jooq.tables.records.FilesAccessPathRecord;
import com.fincity.saas.files.model.FilesAccessPath;
import com.fincity.saas.files.service.FilesAccessPathService;

@RestController
@RequestMapping("api/files/accesspath")
public class FilesAccessPathController extends AbstractJOOQUpdatableDataController<FilesAccessPathRecord, ULong, FilesAccessPath, FilesAccessPathDao, FilesAccessPathService> {

}
