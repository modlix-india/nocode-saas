package com.fincity.saas.files.dao;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.commons.util.ByteUtil;
import com.fincity.saas.files.dto.FilesUploadDownloadDTO;
import com.fincity.saas.files.jooq.tables.FilesUploadDownload;
import com.fincity.saas.files.jooq.tables.records.FilesUploadDownloadRecord;

import reactor.core.publisher.Mono;

@Service
public class FilesUploadDownloadDao
        extends AbstractUpdatableDAO<FilesUploadDownloadRecord, ULong, FilesUploadDownloadDTO> {

    public FilesUploadDownloadDao() {
        super(FilesUploadDownloadDTO.class, FilesUploadDownload.FILES_UPLOAD_DOWNLOAD,
                FilesUploadDownload.FILES_UPLOAD_DOWNLOAD.ID);
    }

    public Mono<Boolean> updateDone(ULong id) {

        return Mono.from(this.dslContext.update(FilesUploadDownload.FILES_UPLOAD_DOWNLOAD)
                .set(FilesUploadDownload.FILES_UPLOAD_DOWNLOAD.IS_DONE, ByteUtil.ONE)
                .where(FilesUploadDownload.FILES_UPLOAD_DOWNLOAD.ID.eq(id)))
                .map(count -> count == 1);
    }
}
