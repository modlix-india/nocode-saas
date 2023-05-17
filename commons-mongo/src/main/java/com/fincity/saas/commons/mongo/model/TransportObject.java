package com.fincity.saas.commons.mongo.model;

import java.io.Serializable;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class TransportObject implements Serializable {

	private static final long serialVersionUID = 8576537166958502323L;

	private String objectType;
	private Map<String, Object> data;

}
