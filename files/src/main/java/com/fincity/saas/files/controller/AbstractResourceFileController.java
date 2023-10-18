package com.fincity.saas.files.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.FileType;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.files.model.DownloadOptions;
import com.fincity.saas.files.model.FileDetail;
import com.fincity.saas.files.service.AbstractFilesResourceService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public abstract class AbstractResourceFileController<T extends AbstractFilesResourceService> {

	@Autowired
	private T service;

	@GetMapping("/**")
	public Mono<ResponseEntity<Page<FileDetail>>> list(Pageable page, @RequestParam(required = false) String filter,
			@RequestParam(required = false) String clientCode, @RequestParam(required = false) FileType[] fileType,
			ServerHttpRequest request) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.service.list(CommonsUtil.nonNullValue(clientCode, ca.getClientCode(), ca.getLoggedInFromClientCode()),
						request.getURI()
								.toString(),
						fileType, filter, page),

				(ca, pg) -> Mono.just(ResponseEntity.<Page<FileDetail>>ok(pg)))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractResourceFileController.list"));
	}

	@DeleteMapping("/**")
	public Mono<ResponseEntity<Boolean>> delete(@RequestParam(required = false) String clientCode,
			ServerHttpRequest request) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.service.delete(CommonsUtil.nonNullValue(clientCode, ca.getClientCode(), ca.getLoggedInFromClientCode()),
						request.getURI()
								.toString()),

				(ca, deleted) -> Mono.just(ResponseEntity.<Boolean>ok(deleted)))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractResourceFileController.delete"));
	}

	@PostMapping("/**")
	public Mono<ResponseEntity<FileDetail>> create(
			@RequestPart(name = "file", required = false) Mono<FilePart> filePart,
			@RequestParam(required = false) String clientCode,
			@RequestPart(required = false, name = "override") String override,
			@RequestPart(required = false, name = "name") String fileName, ServerHttpRequest request) {

		return FlatMapUtil.flatMapMonoWithNull(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> filePart,

		        (ca, fp) -> this.service.create(
		                CommonsUtil.nonNullValue(clientCode, ca.getClientCode(), ca.getLoggedInFromClientCode()),
		                request.getPath()
		                        .toString(),
		                fp, fileName, override != null ? BooleanUtil.safeValueOf(override)
		                        : null))
				.map(ResponseEntity::ok)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractResourceFileController.create"));
	}

	@GetMapping("/file/**")
	public Mono<Void> downloadFile(@RequestParam(required = false) Integer width,
			@RequestParam(required = false) Integer height,
			@RequestParam(required = false, defaultValue = "false") Boolean download,
			@RequestParam(required = false, defaultValue = "true") Boolean keepAspectRatio,
			@RequestParam(required = false) String bandColor,
			@RequestParam(required = false, defaultValue = "HORIZONTAL") DownloadOptions.ResizeDirection resizeDirection,
			@RequestParam(required = false, defaultValue = "false") Boolean noCache, ServerHttpRequest request,
			@RequestParam(required = false) String name, ServerHttpResponse response) {

		return service.downloadFile(new DownloadOptions().setHeight(height)
				.setWidth(width)
				.setKeepAspectRatio(keepAspectRatio)
				.setBandColor(bandColor)
				.setResizeDirection(resizeDirection)
				.setNoCache(noCache)
				.setDownload(download)
				.setName(name), request, response);
	}

	@PostMapping("/import/**")
	public Mono<ResponseEntity<Boolean>> createWithZip(
			@RequestPart(name = "file", required = true) Mono<FilePart> filePart,
			@RequestParam(required = false) String clientCode,
			@RequestPart(required = false, name = "override") String override, ServerHttpRequest request) {

		return FlatMapUtil.flatMapMonoWithNull(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> filePart,

				(ca, fp) -> this.service
						.createFromZipFile(CommonsUtil.nonNullValue(clientCode, ca.getClientCode(), ca.getLoggedInFromClientCode()),
								request.getURI()
										.toString(),
								fp, override != null ? BooleanUtil.safeValueOf(override) : null))
				.map(ResponseEntity::ok)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractResourceFileController.createWithZip"));
	}

}
