package com.fincity.saas.core.dto;

import java.io.Serializable;
import java.util.Map;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class RestResponse implements Serializable {

	private static final long serialVersionUID = 8872204643847233412L;
	private Object data;
	private Map<String, String> headers;
	private int status;

}
