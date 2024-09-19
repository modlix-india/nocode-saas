package com.fincity.saas.ui.enums;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public enum RedirectionType {

	TEMPORARY(HttpStatus.FOUND, HttpStatus.FOUND, HttpStatus.SEE_OTHER, HttpStatus.TEMPORARY_REDIRECT),
	PERMANENT(HttpStatus.MOVED_PERMANENTLY, HttpStatus.MOVED_PERMANENTLY, HttpStatus.PERMANENT_REDIRECT),
	FORWARD(HttpStatus.OK, HttpStatus.OK);

	private final HttpStatus defaultStatus;
	private final Set<HttpStatus> httpStatuses;

	private static final Map<HttpStatus, RedirectionType> STATUS_TYPE_MAP;

	static {
		STATUS_TYPE_MAP = Stream.of(values())
				.flatMap(type -> type.httpStatuses.stream().map(status -> Map.entry(status, type)))
				.collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	RedirectionType(HttpStatus defaultStatus, HttpStatus... statuses) {
		this.defaultStatus = defaultStatus;
		this.httpStatuses = Set.of(statuses);
	}

	public boolean containsStatus(HttpStatus status) {
		return this.httpStatuses.contains(status);
	}

	@Override
	public String toString() {
		return name() + " " + httpStatuses;
	}

	public static RedirectionType resolve(HttpStatus status) {
		return STATUS_TYPE_MAP.get(status);
	}

	public static RedirectionType resolve(int statusCode) {
		return resolve(HttpStatus.resolve(statusCode));
	}
}
