package com.modlix.saas.files.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;
import com.modlix.saas.commons2.util.BooleanUtil;
import com.modlix.saas.commons2.util.CommonsUtil;
import com.modlix.saas.files.model.FileDetail;
import com.modlix.saas.files.model.ImageDetails;
import com.modlix.saas.files.service.SecuredFileResourceService;
import com.modlix.saas.files.service.StaticFileResourceService;
import com.modlix.saas.files.util.ImageDetailsUtil;

import jakarta.servlet.http.HttpServletRequest;

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
    public ResponseEntity<FileDetail> create(
            @PathVariable String resourceType,
            @RequestPart(name = "file", required = false) MultipartFile filePart,
            @RequestParam(required = false) String clientCode,
            @RequestPart(required = false, name = "override") String override,
            @RequestPart(required = false) String width, @RequestPart(required = false) String height,
            @RequestPart(required = false) String rotation, @RequestPart(required = false) String cropAreaX,
            @RequestPart(required = false) String cropAreaY, @RequestPart(required = false) String cropAreaWidth,
            @RequestPart(required = false) String cropAreaHeight,
            @RequestPart(required = false) String flipHorizontal,
            @RequestPart(required = false) String flipVertical, @RequestPart(required = false) String backgroundColor,
            @RequestPart(name = "path", required = false) String filePath,
            @RequestPart(required = false) String fileName, HttpServletRequest request) {

        ImageDetails imageDetails = ImageDetailsUtil.makeDetails(
                width, height, rotation, cropAreaX, cropAreaY, cropAreaWidth, cropAreaHeight, flipHorizontal,
                flipVertical,
                backgroundColor);

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        FileDetail fileDetail = ("secured".equals(resourceType) ? this.securedService : this.staticService).imageUpload(
                CommonsUtil.nonNullValue(clientCode, ca.getClientCode(), ca.getLoggedInFromClientCode()),
                request.getRequestURI(), filePart, fileName,
                BooleanUtil.safeValueOf(override), imageDetails, filePath);

        return ResponseEntity.ok(fileDetail);
    }

}
