package com.modlix.saas.files.dto;

import org.jooq.types.ULong;

import com.modlix.saas.commons2.model.dto.AbstractUpdatableDTO;
import com.modlix.saas.files.jooq.enums.FilesAccessPathResourceType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class FilesAccessPath extends AbstractUpdatableDTO<ULong, ULong> {

    private static final long serialVersionUID = -8318466191600579113L;

    private String clientCode;
    private ULong userId;
    private String accessName;
    private boolean writeAccess;
    private String path;
    private boolean allowSubPathAccess;
    private FilesAccessPathResourceType resourceType;
}
