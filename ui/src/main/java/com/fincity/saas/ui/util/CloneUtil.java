package com.fincity.saas.ui.util;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class CloneUtil {
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static Object cloneObject(Object obj) {

		if (obj == null)
			return obj;

		if (obj instanceof Map map)
			return cloneMapObject(map);

		if (obj instanceof List lst)
			return cloneMapList(lst);

		return obj;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static List cloneMapList(List lst) {

		if (lst == null)
			return List.of();

		return lst.stream()
		        .map(CloneUtil::cloneObject)
		        .filter(Objects::nonNull)
		        .toList();
	}

	public static Map<String, Object> cloneMapObject(Map<String, Object> map) {

		if (map == null)
			return Map.of();

		return map.entrySet()
		        .stream()
		        .filter(e -> Objects.nonNull(e.getValue()))
		        .map(e ->
				{

			        Object k = cloneObject(e.getValue());

			        if (k == null)
				        return null;

			        return Tuples.of(e.getKey(), k);
		        })
		        .filter(Objects::nonNull)
		        .collect(Collectors.toMap(Tuple2::getT1, Tuple2::getT2));
	}

	public static Map<String, Map<String, String>> cloneMapStringMap(Map<String, Map<String, String>> map) {

		if (map == null)
			return Map.of();

		return map.entrySet()
		        .stream()
		        .filter(e -> Objects.nonNull(e.getValue()))
		        .map(e -> Tuples.of(e.getKey(), e.getValue()
		                .entrySet()
		                .stream()
		                .filter(f -> Objects.nonNull(f.getValue()))
		                .map(f -> Tuples.of(f.getKey(), f.getValue()))
		                .collect(Collectors.toMap(Tuple2::getT1, Tuple2::getT2))))
		        .collect(Collectors.toMap(Tuple2::getT1, Tuple2::getT2));
	}

	private CloneUtil() {
	}
}
