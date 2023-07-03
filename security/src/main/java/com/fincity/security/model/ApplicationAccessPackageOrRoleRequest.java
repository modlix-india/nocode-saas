package com.fincity.security.model;

import java.io.Serializable;

import org.jooq.types.ULong;

import lombok.Data;

@Data
public class ApplicationAccessPackageOrRoleRequest implements Serializable{
	
	private static final long serialVersionUID  = 1984759845L;
	
	private ULong clientId;
	private ULong packageId;
	private ULong roleId;
}
