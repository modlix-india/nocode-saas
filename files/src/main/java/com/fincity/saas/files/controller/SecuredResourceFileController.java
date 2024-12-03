package com.fincity.saas.files.controller;

import com.fincity.saas.files.model.DownloadOptions;
import com.fincity.saas.files.model.DownloadOptions.ResizeDirection;
import com.fincity.saas.files.service.SecuredFileResourceService;
import java.time.temporal.ChronoUnit;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/files/secured")
public class SecuredResourceFileController extends AbstractResourceFileController<SecuredFileResourceService> {

	public SecuredResourceFileController(SecuredFileResourceService service) {
		super(service);
	}

	@GetMapping("/createKey/**")
	public Mono<ResponseEntity<String>> generateSecureAccess(@RequestParam(required = false) Long timeSpan,
			@RequestParam(required = false) ChronoUnit timeUnit, @RequestParam(required = false) Long accessLimit,
			ServerHttpRequest request) {

		return this.service.createSecuredAccess(timeSpan, timeUnit, accessLimit, request.getPath()
				.toString())
				.map(ResponseEntity::ok);
	}

	@GetMapping("/downloadFileByKey/{key}")
	public Mono<Void> downloadFileWithKey(@PathVariable(required = true) String key,
			@RequestParam(required = false) Integer width, @RequestParam(required = false) Integer height,
			@RequestParam(required = false, defaultValue = "false") Boolean download,
			@RequestParam(required = false, defaultValue = "true") Boolean keepAspectRatio,
			@RequestParam(required = false) String bandColor,
			@RequestParam(required = false, defaultValue = "HORIZONTAL") ResizeDirection resizeDirection,
			@RequestParam(required = false, defaultValue = "false") Boolean noCache, ServerHttpRequest request,
			ServerHttpResponse response) {

		DownloadOptions downloadOptions = new DownloadOptions().setHeight(height)
				.setWidth(width)
				.setDownload(download)
				.setKeepAspectRatio(keepAspectRatio)
				.setBandColor(bandColor)
				.setResizeDirection(resizeDirection)
				.setNoCache(noCache);

		return this.service.downloadFileByKey(key, downloadOptions, request, response);
	}
}
