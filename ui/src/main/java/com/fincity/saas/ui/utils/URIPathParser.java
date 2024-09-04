package com.fincity.saas.ui.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class URIPathParser {

	private static final char PATH_VARIABLE_START = '{';
	private static final char PATH_VARIABLE_END = '}';

	private URIPathParser() {
		// Private constructor to prevent instantiation
	}

	public static class PathParser {
		private String path;

		private PathParser(String path) {
			this.path = path;
		}

		public PathParser extractPath() {
			this.path = URIPathParser.extractPath(this.path);
			return this;
		}

		public PathParser normalizeAndValidate() {
			this.path = URIPathParser.normalizeAndValidatePath(this.path);
			return this;
		}

		public String build() {
			return this.path;
		}

		public String extractPathPattern() {
			return URIPathParser.extractPathPattern(this.path);
		}

		public String extractJustPath() {
			return URIPathParser.extractJustPath(this.path);
		}

		public List<String> extractPathParams() {
			return URIPathParser.extractPathParams(this.path);
		}
	}

	public static PathParser pathParser(String path) {
		return new PathParser(path);
	}

	public static class QueryParser {

		private final String query;

		private QueryParser(String path) {
			this.query = URIPathParser.extractQuery(path);
		}

		public QueryParser extractQuery() {
			return this;
		}

		public List<String> extractQueryParams() {
			return URIPathParser.extractQueryParams(this.query);
		}

		public String build() {
			return this.query;
		}
	}

	public static QueryParser queryParser(String path) {
		return new QueryParser(path);
	}

	private static String extractPath(String path) {

		int queryIndex = path.indexOf('?');

		return queryIndex == -1 ? path : path.substring(0, queryIndex);
	}

	private static String extractQuery(String path) {

		int queryIndex = path.indexOf('?');

		return queryIndex == -1 ? "" : path.substring(queryIndex + 1);
	}

	private static String normalizeAndValidatePath(String path) {
		if (path.isEmpty())
			return "/";
		if (path.charAt(0) != '/')
			path = '/' + path;
		return path.replaceAll("/{2,}", "/");
	}

	private static String extractPathPattern(String path) {
		if (path.isEmpty())
			return "/";

		StringBuilder sb = new StringBuilder(path.length());
		int start = 1;
		sb.append('/');

		while (start < path.length()) {
			int end = path.indexOf('/', start);
			if (end == -1)
				end = path.length();

			if (start < end) {
				String segment = path.substring(start, end);
				sb.append(isPathVariable(segment) ? '*' : segment);
				if (end < path.length())
					sb.append('/');
			}

			start = end + 1;
		}

		return sb.toString();
	}

	private static String extractJustPath(String path) {
		if (path.isEmpty())
			return "/";

		StringBuilder sb = new StringBuilder(path.length());
		int start = 1;
		sb.append('/');

		while (start < path.length()) {
			int end = path.indexOf('/', start);
			if (end == -1)
				end = path.length();

			if (start < end) {
				String segment = path.substring(start, end);
				if (!isPathVariable(segment)) {
					sb.append(segment).append('/');
				}
			}

			start = end + 1;
		}

		if (sb.length() > 1 && sb.charAt(sb.length() - 1) == '/') {
			sb.setLength(sb.length() - 1);
		}

		return sb.toString();
	}

	private static List<String> extractPathParams(String path) {
		if (path.isEmpty())
			return Collections.emptyList();

		List<String> params = new ArrayList<>();
		int start = 1;

		while (start < path.length()) {
			int end = path.indexOf('/', start);
			if (end == -1)
				end = path.length();

			if (start < end) {
				String segment = path.substring(start, end);
				if (isPathVariable(segment)) {
					params.add(extractVariableName(segment));
				}
			}

			start = end + 1;
		}

		return params;
	}

	private static List<String> extractQueryParams(String query) {
		if (query.isEmpty())
			return Collections.emptyList();

		List<String> params = new ArrayList<>();
		int start = 0;

		while (start < query.length()) {
			int end = query.indexOf('&', start);
			if (end == -1)
				end = query.length();

			int equalIndex = query.indexOf('=', start);
			if (equalIndex != -1 && equalIndex < end) {
				String key = query.substring(start, equalIndex);
				params.add(isPathVariable(key) ? extractVariableName(key) : key);
			}

			start = end + 1;
		}

		return params;
	}

	private static boolean isPathVariable(String segment) {
		return segment.length() > 2 &&
				segment.charAt(0) == PATH_VARIABLE_START &&
				segment.charAt(segment.length() - 1) == PATH_VARIABLE_END;
	}

	private static String extractVariableName(String segment) {
		return segment.substring(1, segment.length() - 1);
	}
}
