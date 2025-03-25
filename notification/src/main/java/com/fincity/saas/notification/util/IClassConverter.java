package com.fincity.saas.notification.util;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public interface IClassConverter {

	Gson GSON = new Gson();

	@JsonIgnore
	default Map<String, Object> toMap() {
		return GSON.fromJson(GSON.toJson(this), new TypeToken<Map<String, Object>>() {
		}.getType());
	}
}
