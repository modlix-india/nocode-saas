package com.fincity.saas.files.controller;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.files.model.FileDetail;
import com.fincity.saas.files.service.StaticFileResourceService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/files/static")
public class StaticResourceFileController {

	private static final String URI_PART = "api/files/static";
	private static final int URI_PART_LENGTH = URI_PART.length();

	private static final String URI_PART_FILE = URI_PART + "/file";
	private static final int URI_PART_FILE_LENGTH = URI_PART_FILE.length();

	@Autowired
	private StaticFileResourceService service;

	@GetMapping("/**")
	public Mono<ResponseEntity<Page<FileDetail>>> list(Pageable page, @RequestParam(required = false) String filter,
	        ServerHttpRequest request) {

		String uri = request.getURI()
		        .toString();

		String path = uri.substring(uri.indexOf(URI_PART) + URI_PART_LENGTH,
		        uri.length() - (uri.endsWith("/") ? 1 : 0));

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> this.service.list(ca.getLoggedInFromClientCode(), path, filter, page),

		        (ca, pg) -> Mono.just(ResponseEntity.<Page<FileDetail>>ok(pg)));
	}

	@GetMapping("/file/**")
	public Mono<Void> downloadFile(ServerHttpRequest request, ServerHttpResponse response) throws IOException {

		String uri = request.getURI()
		        .toString();

		String path = uri.substring(uri.indexOf(URI_PART_FILE) + URI_PART_FILE_LENGTH,
		        uri.length() - (uri.endsWith("/") ? 1 : 0));

		return service.downloadFile(path, response);
	}
}
