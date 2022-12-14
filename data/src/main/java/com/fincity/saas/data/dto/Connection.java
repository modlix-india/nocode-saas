package com.fincity.saas.data.dto;

import java.util.Map;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.data.jooq.enums.DataConnectionDbType;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Connection extends AbstractUpdatableDTO<ULong, ULong> {

	private static final long serialVersionUID = 4900712251397498217L;

	private String clientCode;
	private String dbName;
	private DataConnectionDbType dbType;
	private Map<String, Object> dbConnection; //NOSONAR
	// Storing unknown data as connection details.
	private boolean defaultDb;
}
