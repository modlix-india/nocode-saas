package com.modlix.saas.files.dao;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.modlix.saas.commons2.jooq.dao.AbstractUpdatableDAO;
import com.modlix.saas.files.dto.FilesUploadDownloadDTO;
import com.modlix.saas.files.jooq.enums.FilesUploadDownloadStatus;
import com.modlix.saas.files.jooq.tables.FilesUploadDownload;
import com.modlix.saas.files.jooq.tables.records.FilesUploadDownloadRecord;

@Service
public class FilesUploadDownloadDao
        extends AbstractUpdatableDAO<FilesUploadDownloadRecord, ULong, FilesUploadDownloadDTO> {

    public FilesUploadDownloadDao() {
        super(FilesUploadDownloadDTO.class, FilesUploadDownload.FILES_UPLOAD_DOWNLOAD,
                FilesUploadDownload.FILES_UPLOAD_DOWNLOAD.ID);
    }

    public boolean updateDone(ULong id) {

        return this.dslContext.update(FilesUploadDownload.FILES_UPLOAD_DOWNLOAD)
                .set(FilesUploadDownload.FILES_UPLOAD_DOWNLOAD.STATUS, FilesUploadDownloadStatus.DONE)
                .where(FilesUploadDownload.FILES_UPLOAD_DOWNLOAD.ID.eq(id)).execute() == 1;
    }

    public boolean updateFailed(Throwable e, ULong id) {

        return this.dslContext.update(FilesUploadDownload.FILES_UPLOAD_DOWNLOAD)
                .set(FilesUploadDownload.FILES_UPLOAD_DOWNLOAD.STATUS, FilesUploadDownloadStatus.ERROR)
                .set(FilesUploadDownload.FILES_UPLOAD_DOWNLOAD.EXCEPTION, e.getMessage())
                .where(FilesUploadDownload.FILES_UPLOAD_DOWNLOAD.ID.eq(id)).execute() == 1;
    }
}
