package com.fincity.saas.files.controller;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.files.enums.ImagesFormatForResize;
import com.fincity.saas.files.model.FileDetail;
import com.fincity.saas.files.model.ImageDetails;
import com.fincity.saas.files.service.FilesMessageResourceService;
import com.fincity.saas.files.service.SecuredFileResourceService;
import com.fincity.saas.files.service.StaticFileResourceService;
import com.fincity.saas.files.util.ImageDetailsUtil;
import com.fincity.saas.files.util.ImageTransformUtil;
import org.jooq.types.ULong;
import org.springframework.http.*;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;

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

    //Currently only supports jpg and png.
    @PostMapping("/resize")
    public Mono<Void> resizeImage(
        @RequestPart(name = "file") Mono<FilePart> filePart,
        @RequestPart(required = false) String width, @RequestPart(required = false) String height,
        @RequestPart(required = false) String rotation, @RequestPart(required = false) String cropAreaX,
        @RequestPart(required = false) String cropAreaY, @RequestPart(required = false) String cropAreaWidth,
        @RequestPart(required = false) String cropAreaHeight,
        @RequestPart(required = false) String flipHorizontal,
        @RequestPart(required = false) String flipVertical, @RequestPart(required = false) String backgroundColor,
        @RequestPart(required = false) ImagesFormatForResize toFormat,
        ServerHttpResponse response) {

        ImageDetails imageDetails = ImageDetailsUtil.makeDetails(
            width, height, rotation, cropAreaX, cropAreaY, cropAreaWidth, cropAreaHeight, flipHorizontal, flipVertical,
            backgroundColor
        );

        Mono<Tuple3<BufferedImage, String, Integer>> convertedTuple = FlatMapUtil.flatMapMono(

            () -> filePart,

            fp -> Mono.fromCallable(() -> Files.createTempDirectory("imageUpload"))
                .map(fld -> fld.resolve(fp.filename()))
                .flatMap(path -> fp.transferTo(path).thenReturn(path))
                .subscribeOn(Schedulers.boundedElastic()),

            (fp, file) -> Mono.fromCallable(() -> ImageTransformUtil.makeSourceImage(file.toFile(), fp.filename()))
                .subscribeOn(Schedulers.boundedElastic()),

            (fp, file, srcTuple) -> Mono.just(CommonsUtil.nonNullValue(toFormat, ImagesFormatForResize.fromFileName(fp.filename())) == ImagesFormatForResize.PNG
                ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB),

            (fp, file, srcTuple, imageTargetType) -> Mono.fromCallable(() ->
                    Tuples.of(ImageTransformUtil.transformImage(srcTuple.getT1(), imageTargetType, imageDetails), fp.filename(), imageTargetType))
                .subscribeOn(Schedulers.boundedElastic())
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "GenericResourceFileController.resizeImage"));

        return FlatMapUtil.flatMapMono(
                () -> convertedTuple,

                tup -> {
                    String targetFileName = tup.getT2();
                    int imageTargetType = tup.getT3();
                    String lowerName = targetFileName.toLowerCase();

                    if ((imageTargetType == BufferedImage.TYPE_INT_ARGB && !lowerName.endsWith(".png")) ||
                        (imageTargetType == BufferedImage.TYPE_INT_RGB && (!lowerName.endsWith(".jpg") && !lowerName.endsWith(".jpeg")))) {
                        int index = targetFileName.lastIndexOf('.');
                        targetFileName = (index > 0 ? targetFileName.substring(0, index) : targetFileName) +
                            (lowerName.endsWith(".jpg") ? ".png" : ".jpg");
                    }
                    return Mono.just(targetFileName);
                },

                (tup, tFileName) -> {
                    BufferedImage img = tup.getT1();
                    int imageTargetType = tup.getT3();

                    return Mono.fromCallable(() -> {
                            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                            ImageIO.write(img, imageTargetType == BufferedImage.TYPE_INT_ARGB ? "png" : "jpg", byteStream);
                            return byteStream;
                        }).onErrorResume(ex -> this.msgService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                            FilesMessageResourceService.IMAGE_TRANSFORM_ERROR, ex))
                        .subscribeOn(Schedulers.boundedElastic());
                },

                (tup, targetFileName, byteStream) -> {
                    HttpHeaders respHeaders = response.getHeaders();
                    respHeaders.setContentDisposition(ContentDisposition.inline().filename(targetFileName).build());
                    respHeaders.setContentType(tup.getT3() == BufferedImage.TYPE_INT_ARGB ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG);
                    ZeroCopyHttpOutputMessage zeroCopyResponse = (ZeroCopyHttpOutputMessage) response;
                    respHeaders.setContentLength(byteStream.size());
                    return zeroCopyResponse.writeWith(Mono.just(response.bufferFactory().wrap(byteStream.toByteArray())));
                }
            )
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "GenericResourceFileController.resizeImage (Part 2)"));
    }
}
