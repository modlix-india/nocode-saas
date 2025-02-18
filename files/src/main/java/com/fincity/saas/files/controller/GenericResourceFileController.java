package com.fincity.saas.files.controller;

import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.files.model.FileDetail;
import com.fincity.saas.files.model.ImageDetails;
import com.fincity.saas.files.service.SecuredFileResourceService;
import com.fincity.saas.files.service.StaticFileResourceService;
import com.fincity.saas.files.util.ImageDetailsUtil;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/files/generic")
public class GenericResourceFileController {

    private final SecuredFileResourceService securedService;
    private final StaticFileResourceService staticService;

    public GenericResourceFileController(SecuredFileResourceService securedService,
                                         StaticFileResourceService staticService) {
        this.securedService = securedService;
        this.staticService = staticService;
    }

    @PostMapping("/client")
    public Mono<ResponseEntity<FileDetail>> uploadClientImage(
        @RequestPart(name = "file") Mono<FilePart> filePart,
        @RequestPart(required = false) String width, @RequestPart(required = false) String height,
        @RequestPart(required = false) String rotation, @RequestPart(required = false) String cropAreaX,
        @RequestPart(required = false) String cropAreaY, @RequestPart(required = false) String cropAreaWidth,
        @RequestPart(required = false) String cropAreaHeight,
        @RequestPart(required = false) String flipHorizontal,
        @RequestPart(required = false) String flipVertical, @RequestPart(required = false) String backgroundColor,
        @RequestParam(name = "clientId", required = false) ULong clientId) {

        ImageDetails imageDetails = ImageDetailsUtil.makeDetails(
            width, height, rotation, cropAreaX, cropAreaY, cropAreaWidth, cropAreaHeight, flipHorizontal, flipVertical,
            backgroundColor
        );

        return filePart.flatMap(fp -> this.staticService.uploadClientImage(fp, imageDetails, clientId)).map(ResponseEntity::ok);
    }

    @PostMapping("/user")
    public Mono<ResponseEntity<FileDetail>> uploadUserImage(
        @RequestPart(name = "file") Mono<FilePart> filePart,
        @RequestPart(required = false) String width, @RequestPart(required = false) String height,
        @RequestPart(required = false) String rotation, @RequestPart(required = false) String cropAreaX,
        @RequestPart(required = false) String cropAreaY, @RequestPart(required = false) String cropAreaWidth,
        @RequestPart(required = false) String cropAreaHeight,
        @RequestPart(required = false) String flipHorizontal,
        @RequestPart(required = false) String flipVertical, @RequestPart(required = false) String backgroundColor,
        @RequestParam(name = "userId", required = false) ULong userId) {

        ImageDetails imageDetails = ImageDetailsUtil.makeDetails(
            width, height, rotation, cropAreaX, cropAreaY, cropAreaWidth, cropAreaHeight, flipHorizontal, flipVertical,
            backgroundColor
        );

        return filePart.flatMap(fp -> this.securedService.uploadUserImage(fp, imageDetails, userId)).map(ResponseEntity::ok);
    }
}
