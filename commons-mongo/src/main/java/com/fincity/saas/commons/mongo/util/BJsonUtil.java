package com.fincity.saas.commons.mongo.util;

import java.util.Map;

import org.bson.Document;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class BJsonUtil {

	public static Document from(JsonObject job) {

		@SuppressWarnings("unchecked")
		Map<String, Object> map = new Gson().fromJson(job, Map.class);
		return new Document(map);
	}

	private BJsonUtil() {
	}
}
