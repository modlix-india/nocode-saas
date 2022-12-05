package com.fincity.saas.data.dto;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.data.jooq.enums.DataStorageRefFieldRefType;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class StorageRefField extends AbstractUpdatableDTO<ULong, ULong> {

	private static final long serialVersionUID = 4818996620726412681L;

	private ULong storageFieldId;
	private ULong refStorageId;
	private DataStorageRefFieldRefType refType;
}
