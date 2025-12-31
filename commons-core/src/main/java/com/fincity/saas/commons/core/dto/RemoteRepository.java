package com.fincity.saas.commons.core.dto;

import java.io.Serial;

import org.jooq.types.ULong;

import com.fincity.saas.commons.core.jooq.enums.CoreRemoteRepositoriesRepoName;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class RemoteRepository extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 1L;

    private String appCode;
    private CoreRemoteRepositoriesRepoName repoName;
}
