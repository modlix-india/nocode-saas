package com.fincity.saas.files.dto;

import java.time.LocalDateTime;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class FilesSecuredAccessKey extends AbstractDTO<ULong, ULong> {

	private static final long serialVersionUID = -8642727525010446777L;

	private String path;
	private String accessKey;
	private LocalDateTime accessTill;
	private ULong accessLimit;
	private ULong accessedCount;

}
