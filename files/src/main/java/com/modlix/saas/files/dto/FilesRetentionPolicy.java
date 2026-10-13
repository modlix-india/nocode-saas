package com.modlix.saas.files.dto;

import org.jooq.types.ULong;

import com.modlix.saas.commons2.model.dto.AbstractUpdatableDTO;
import com.modlix.saas.files.jooq.enums.FilesRetentionPolicyAppliesTo;
import com.modlix.saas.files.jooq.enums.FilesRetentionPolicyLevel;
import com.modlix.saas.files.jooq.enums.FilesRetentionPolicyResourceType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * "Files under {@code <appCode>/<path>} older than {@code retentionDays} days are removed."
 *
 * <p>{@code path} is the prefix after the app folder, without leading or trailing slashes; an empty
 * path is the whole app folder. A null {@code retentionDays} means keep forever.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class FilesRetentionPolicy extends AbstractUpdatableDTO<ULong, ULong> {

    private static final long serialVersionUID = 4019270356981262871L;

    private String appCode;
    private String clientCode;
    private FilesRetentionPolicyResourceType resourceType;
    private String path;
    private Integer retentionDays;
    private FilesRetentionPolicyAppliesTo appliesTo;
    private FilesRetentionPolicyLevel level;
}
