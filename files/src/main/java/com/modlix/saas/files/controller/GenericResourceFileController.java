package com.modlix.saas.files.controller;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.jooq.types.ULong;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.files.enums.ImagesFormatForResize;
import com.modlix.saas.files.model.FileDetail;
import com.modlix.saas.files.model.ImageDetails;
import com.modlix.saas.files.service.FilesMessageResourceService;
import com.modlix.saas.files.service.SecuredFileResourceService;
import com.modlix.saas.files.service.StaticFileResourceService;
import com.modlix.saas.files.util.ImageDetailsUtil;
import com.modlix.saas.files.util.ImageTransformUtil;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("api/files/generic")
public class GenericResourceFileController {

    private final SecuredFileResourceService securedService;
    private final StaticFileResourceService staticService;
    private final FilesMessageResourceService msgService;

    public GenericResourceFileController(SecuredFileResourceService securedService,
                                         StaticFileResourceService staticService,
                                         FilesMessageResourceService msgService) {
        this.securedService = securedService;
        this.staticService = staticService;
        this.msgService = msgService;
    }

    @PostMapping("/client")
    public ResponseEntity<FileDetail> uploadClientImage(
            @RequestPart(name = "file") MultipartFile filePart,
            @RequestPart(required = false) String width, @RequestPart(required = false) String height,
            @RequestPart(required = false) String rotation, @RequestPart(required = false) String cropAreaX,
            @RequestPart(required = false) String cropAreaY, @RequestPart(required = false) String cropAreaWidth,
            @RequestPart(required = false) String cropAreaHeight,
            @RequestPart(required = false) String flipHorizontal,
            @RequestPart(required = false) String flipVertical, @RequestPart(required = false) String backgroundColor,
            @RequestParam(name = "clientId", required = false) ULong clientId) {

        ImageDetails imageDetails = ImageDetailsUtil.makeDetails(
                width, height, rotation, cropAreaX, cropAreaY, cropAreaWidth, cropAreaHeight, flipHorizontal,
                flipVertical,
                backgroundColor);

        return ResponseEntity.ok(this.staticService.uploadClientImage(filePart, imageDetails, clientId));
    }

    @PostMapping("/user")
    public ResponseEntity<FileDetail> uploadUserImage(
            @RequestPart(name = "file") MultipartFile filePart,
            @RequestPart(required = false) String width, @RequestPart(required = false) String height,
            @RequestPart(required = false) String rotation, @RequestPart(required = false) String cropAreaX,
            @RequestPart(required = false) String cropAreaY, @RequestPart(required = false) String cropAreaWidth,
            @RequestPart(required = false) String cropAreaHeight,
            @RequestPart(required = false) String flipHorizontal,
            @RequestPart(required = false) String flipVertical, @RequestPart(required = false) String backgroundColor,
            @RequestParam(name = "userId", required = false) ULong userId) {

        ImageDetails imageDetails = ImageDetailsUtil.makeDetails(
                width, height, rotation, cropAreaX, cropAreaY, cropAreaWidth, cropAreaHeight, flipHorizontal,
                flipVertical,
                backgroundColor);

        return ResponseEntity.ok(this.securedService.uploadUserImage(filePart, imageDetails, userId));
    }

    // Currently only supports jpg and png.
    @PostMapping("/resize")
    public void resizeImage(
            @RequestPart(name = "file") MultipartFile fp,
            @RequestPart(required = false) String width, @RequestPart(required = false) String height,
            @RequestPart(required = false) String rotation, @RequestPart(required = false) String cropAreaX,
            @RequestPart(required = false) String cropAreaY, @RequestPart(required = false) String cropAreaWidth,
            @RequestPart(required = false) String cropAreaHeight,
            @RequestPart(required = false) String flipHorizontal,
            @RequestPart(required = false) String flipVertical, @RequestPart(required = false) String backgroundColor,
            @RequestPart(required = false) ImagesFormatForResize toFormat,
            HttpServletResponse response) {

        ImageDetails imageDetails = ImageDetailsUtil.makeDetails(
                width, height, rotation, cropAreaX, cropAreaY, cropAreaWidth, cropAreaHeight, flipHorizontal,
                flipVertical,
                backgroundColor);

        try {
            Path fld = Files.createTempDirectory("imageUpload");
            Path path = fld.resolve(fp.getOriginalFilename());
            fp.transferTo(path);

            var srcTuple = ImageTransformUtil.makeSourceImage(path.toFile(), fp.getOriginalFilename());
            int imageTargetType = ImagesFormatForResize
                    .fromFileName(fp.getOriginalFilename()) == ImagesFormatForResize.PNG
                    ? BufferedImage.TYPE_INT_ARGB
                    : BufferedImage.TYPE_INT_RGB;

            BufferedImage transformedImage = ImageTransformUtil.transformImage(srcTuple.getT1(),
                    imageTargetType, imageDetails);

            String targetFileName = fp.getOriginalFilename();
            String lowerName = targetFileName.toLowerCase();

            if ((imageTargetType == BufferedImage.TYPE_INT_ARGB && !lowerName.endsWith(".png")) ||
                    (imageTargetType == BufferedImage.TYPE_INT_RGB
                            && (!lowerName.endsWith(".jpg") && !lowerName.endsWith(".jpeg")))) {
                int index = targetFileName.lastIndexOf('.');
                targetFileName = (index > 0 ? targetFileName.substring(0, index) : targetFileName) +
                        (lowerName.endsWith(".jpg") ? ".png" : ".jpg");
            }

            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

            ImageIO.write(transformedImage, imageTargetType == BufferedImage.TYPE_INT_ARGB ? "png" : "jpg", byteStream);

            response.setHeader("Content-Disposition",
                    ContentDisposition.inline().filename(targetFileName).build().toString());
            response.setHeader("Content-Type",
                    imageTargetType == BufferedImage.TYPE_INT_ARGB ? "image/png" : "image/jpeg");
            response.setHeader("Content-Length", String.valueOf(byteStream.size()));
            response.getOutputStream().write(byteStream.toByteArray());
            response.flushBuffer();
        } catch (IOException e) {
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                    FilesMessageResourceService.IMAGE_TRANSFORM_ERROR, e.getMessage(), e);
        }
    }
}
