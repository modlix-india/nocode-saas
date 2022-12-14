package com.fincity.saas.core.model;

import java.io.Serializable;

import com.google.gson.JsonObject;

import lombok.Data;

@Data
public class DataObject implements Serializable {

	private static final long serialVersionUID = 2698669653996010003L;

	private String message;
	private JsonObject data; // NOSONAR
}
