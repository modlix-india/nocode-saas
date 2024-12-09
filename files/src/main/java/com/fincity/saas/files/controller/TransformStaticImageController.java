package com.fincity.saas.files.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PathVariable;
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
import com.fincity.saas.files.service.SecuredFileResourceService;
import com.fincity.saas.files.service.StaticFileResourceService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@RestController
@RequestMapping("api/files/transform/")
public class TransformStaticImageController {

	private final StaticFileResourceService staticService;
	private final SecuredFileResourceService securedService;

	public TransformStaticImageController(StaticFileResourceService staticService,
			SecuredFileResourceService securedService) {

		this.staticService = staticService;
		this.securedService = securedService;
	}

	@PostMapping(value = "{resourceType}/**", consumes = { "multipart/form-data" })
	public Mono<ResponseEntity<FileDetail>> create(
			@PathVariable String resourceType,
			@RequestPart(name = "file", required = false) Mono<FilePart> filePart,
			@RequestParam(required = false) String clientCode,
			@RequestPart(required = false, name = "override") String override,
			@RequestPart(required = false) String width, @RequestPart(required = false) String height,
			@RequestPart(required = false) String rotation, @RequestPart(required = false) String cropAreaX,
			@RequestPart(required = false) String cropAreaY, @RequestPart(required = false) String cropAreaWidth,
			@RequestPart(required = false) String cropAreaHeight,
			@RequestPart(required = false) String flipHorizontal,
			@RequestPart(required = false) String flipVertical, @RequestPart(required = false) String backgroundColor,
			@RequestPart(name = "path", required = false) String filePath,
			@RequestPart(required = false) String fileName, ServerHttpRequest request) {

		ImageDetails imageDetails = new ImageDetails()
				.setFlipHorizontal(BooleanUtil.safeValueOf(flipHorizontal))
				.setFlipVertical(BooleanUtil.safeValueOf(flipVertical))
				.setBackgroundColor(backgroundColor);

		if (width != null)
			imageDetails.setWidth(Integer.valueOf(width));

		if (height != null)
			imageDetails.setHeight(Integer.valueOf(height));

		if (rotation != null)
			imageDetails.setRotation(Integer.valueOf(rotation));

		if (cropAreaX != null)
			imageDetails.setCropAreaX(Integer.valueOf(cropAreaX));

		if (cropAreaY != null)
			imageDetails.setCropAreaY(Integer.valueOf(cropAreaY));

		if (cropAreaWidth != null)
			imageDetails.setCropAreaWidth(Integer.valueOf(cropAreaWidth));

		if (cropAreaHeight != null)
			imageDetails.setCropAreaHeight(Integer.valueOf(cropAreaHeight));

		return FlatMapUtil.flatMapMonoWithNull(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> filePart,

				(ca, fp) -> ("secured".equals(resourceType) ? this.securedService : this.staticService).imageUpload(
						CommonsUtil.nonNullValue(clientCode, ca.getClientCode(), ca.getLoggedInFromClientCode()),
						request.getPath().toString(), fp, fileName,
						BooleanUtil.safeValueOf(override), imageDetails, filePath))
				.map(ResponseEntity::ok)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "TransformStaticImageController.create"));
	}

}
