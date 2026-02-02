package com.modlix.saas.files.dto;

import org.jooq.types.ULong;

import com.modlix.saas.commons2.model.dto.AbstractUpdatableDTO;
import com.modlix.saas.files.jooq.enums.FilesUploadDownloadResourceType;
import com.modlix.saas.files.jooq.enums.FilesUploadDownloadType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ToString(callSuper = true)
public class FilesUploadDownloadDTO extends AbstractUpdatableDTO<ULong, ULong> {

    private static final long serialVersionUID = -8642727525010446777L;

    private FilesUploadDownloadType type;
    private FilesUploadDownloadResourceType resourceType;
    private String clientCode;
    private String path;
    private String cdnUrl;
    private String status;
    private String exception;
}
