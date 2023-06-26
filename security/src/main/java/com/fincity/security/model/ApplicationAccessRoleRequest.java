package com.fincity.security.model;

import java.io.Serializable;

import org.jooq.types.ULong;

import lombok.Data;

@Data
public class ApplicationAccessRoleRequest implements Serializable{

	private static final long serialVersionUID  = 1347598045L;

	private ULong clientId;
	private ULong roleId;
}
