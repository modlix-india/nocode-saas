package com.fincity.security.model;

import java.io.Serializable;

import org.jooq.types.ULong;

import lombok.Data;

@Data
public class ApplicationAccessPackageRequest implements Serializable{
	
	private static final long serialVersionUID  = 1984759845L;
	
	private ULong clientId;
	private ULong packageId;
}
