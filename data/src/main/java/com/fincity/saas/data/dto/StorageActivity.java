package com.fincity.saas.data.dto;

import java.util.Map;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class StorageActivity extends AbstractUpdatableDTO<ULong, ULong> {

	private static final long serialVersionUID = -4743088946586196865L;
	
	private ULong storageId;
	private String operation;
	private Map<String, Object> opData; //NOSONAR
	// Need to store generic data
}
