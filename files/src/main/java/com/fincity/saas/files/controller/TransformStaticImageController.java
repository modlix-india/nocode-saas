package com.fincity.saas.files.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.files.model.FileDetail;
import com.fincity.saas.files.model.ImageDetails;
import com.fincity.saas.files.service.StaticFileResourceService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@RestController
@RequestMapping("api/files/transform/static")
public class TransformStaticImageController {

	@Autowired
	private StaticFileResourceService service;

	@PostMapping("/**")
	public Mono<ResponseEntity<FileDetail>> create(
			@RequestPart(name = "file", required = false) Mono<FilePart> filePart,
			@RequestParam(required = false) String clientCode,
			@RequestPart(required = false, name = "override") String override,
			@RequestPart(required = false, name = "name") String fileName, ServerHttpRequest request,
			@RequestPart(required = false) String width, @RequestPart(required = false) String height,
			@RequestPart(required = false) String rotation, @RequestPart(required = false) String cropAreaX,
			@RequestPart(required = false) String cropAreaY, @RequestPart(required = false) String cropAreaWidth,
			@RequestPart(required = false) String cropAreaHeight, @RequestPart(required = false) String flipHorizontal,
			@RequestPart(required = false) String flipVertical, @RequestPart(required = false) String backgroundColor,
			@RequestPart(name="path" ,required = false) String filePath) {
		
		ImageDetails imageDetails = new ImageDetails()
				.setWidth(Integer.parseInt(width))
				.setHeight(Integer.parseInt(height))
				.setRotation(Integer.parseInt(rotation))
				.setCropAreaX(Integer.parseInt(cropAreaX))
				.setCropAreaY(Integer.parseInt(cropAreaY))
				.setCropAreaWidth(Integer.parseInt(cropAreaWidth))
				.setCropAreaHeight(Integer.parseInt(cropAreaHeight))
				.setFlipHorizontal(flipHorizontal != null ? BooleanUtil.safeValueOf(flipHorizontal) : null)
				.setFlipVertical(flipVertical != null ? BooleanUtil.safeValueOf(flipVertical) : null)
				.setBackgroundColor(backgroundColor);

		return FlatMapUtil.flatMapMonoWithNull(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> filePart,

				(ca, fp) -> this.service.imageUpload(
						CommonsUtil.nonNullValue(clientCode, ca.getClientCode(), ca.getLoggedInFromClientCode()),
						request.getPath().toString(), fp, fileName,
						override != null ? BooleanUtil.safeValueOf(override) : null, imageDetails, filePath))
				.map(ResponseEntity::ok)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "TransformStaticImageController.create"));
	}

}
