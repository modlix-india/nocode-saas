package com.fincity.security.model;

import java.io.Serializable;

import org.jooq.types.ULong;

import lombok.Data;

@Data
public class ApplicationAccessRequest implements Serializable{
	
	private static final long serialVersionUID = -6637637284829503461L;
	
	private ULong id;
	private ULong clientId;
	private boolean writeAccess;
}
