package com.fincity.saas.files.dao;

import static com.fincity.saas.files.jooq.tables.FilesAccessPath.FILES_ACCESS_PATH;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.files.jooq.tables.records.FilesAccessPathRecord;
import com.fincity.saas.files.model.FilesAccessPath;

@Service
public class FileAccessPathDao extends AbstractUpdatableDAO<FilesAccessPathRecord, ULong, FilesAccessPath> {

	protected FileAccessPathDao() {
		super(FilesAccessPath.class, FILES_ACCESS_PATH, FILES_ACCESS_PATH.ID);
	}
}
