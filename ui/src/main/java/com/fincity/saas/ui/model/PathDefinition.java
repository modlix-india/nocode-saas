package com.fincity.saas.ui.model;

import static com.fincity.saas.ui.utils.URIPathParser.PathParser;
import static com.fincity.saas.ui.utils.URIPathParser.QueryParser;
import static com.fincity.saas.ui.utils.URIPathParser.pathParser;
import static com.fincity.saas.ui.utils.URIPathParser.queryParser;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fincity.saas.commons.mongo.difference.IDifferentiable;
import com.fincity.saas.commons.mongo.util.CloneUtil;

import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;

@Data
@NoArgsConstructor
public class PathDefinition implements Serializable, IDifferentiable<PathDefinition> {

	private String pathString;
	private String path;
	private String pathPattern;
	private String justPath;
	private List<String> pathParams;
	private String query;
	private List<String> queryParams;

	public PathDefinition(String pathString) {

		PathParser pathParser = pathParser(pathString).normalizeAndValidate();
		QueryParser queryParser = queryParser(pathString);

		this.pathString = pathParser.build();
		this.path = pathParser.extractPath().build();
		this.pathPattern = pathParser.extractPathPattern();
		this.justPath = pathParser.extractJustPath();
		this.pathParams = pathParser.extractPathParams();
		this.query = queryParser.build();
		this.queryParams = queryParser.extractQueryParams();
	}

	public PathDefinition(PathDefinition pathDefinition) {

		this.pathString = pathDefinition.pathString;
		this.path = pathDefinition.path;
		this.pathPattern = pathDefinition.pathPattern;
		this.justPath = pathDefinition.justPath;
		this.pathParams = CloneUtil.cloneMapList(pathDefinition.pathParams);
		this.query = pathDefinition.query;
		this.queryParams = CloneUtil.cloneMapList(pathDefinition.queryParams);
	}

	public Map<String, String> extractPathParameters(String incomingPath) {
		Map<String, String> params = new LinkedHashMap<>();

		if (pathParams.isEmpty()) {
			return params;
		}

		String iPath = pathParser(incomingPath).normalizeAndValidate().extractPath().build();

		String[] incomingSegments = iPath.split("/", -1);
		String[] definitionSegments = path.split("/", -1);

		int paramIndex = 0;

		for (int i = 0; i < definitionSegments.length && i < incomingSegments.length; i++) {
			if (definitionSegments[i].startsWith("{") && definitionSegments[i].endsWith("}")) {
				params.put(pathParams.get(paramIndex++), incomingSegments[i]);
			}
		}

		return params;
	}

	@Override
	public Mono<PathDefinition> extractDifference(PathDefinition inc) {
		return null;
	}

	@Override
	public Mono<PathDefinition> applyOverride(PathDefinition override) {
		return null;
	}
}
