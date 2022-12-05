package com.fincity.saas.data.dto;

import java.util.List;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Storage extends AbstractUpdatableDTO<ULong, ULong>{

	private static final long serialVersionUID = 2036189433494750985L;
	
	private String clientCode;
	private String appCode;
	private String namespace;
	private String name;
	private String dbName;
	private Boolean isVersioned;
	private Boolean isAudited;
	
	private List<StorageField> fields;
	private List<StorageRefField> refs;
}
