package com.fincity.saas.core.dto;

import java.util.Map;

import org.springframework.util.MultiValueMap;

import com.google.gson.JsonElement;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class RestRequest {

	private String url;
	private MultiValueMap<String, String> headers;
	private Map<String, String> pathParameters;
	private Map<String, String> queryParameters;
	private int timeout = 300;
	private String method;
	private JsonElement payload;
	private boolean ignoreDefaultHeaders = false;
}
